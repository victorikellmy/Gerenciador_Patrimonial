package com.fundacao.gerenciador_patrimonial.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundacao.gerenciador_patrimonial.domain.entity.PendenciaPatrimoniamento;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusPendencia;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoAnexo;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.PatrimonioResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.PendenciaResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.PendenciaResponse.ItemPendencia;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.PendenciaPatrimoniamentoRepository;
import com.fundacao.gerenciador_patrimonial.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fluxo do operador sobre as pendências de patrimoniamento: listar, abrir,
 * pré-preencher o cadastro do bem, patrimoniar (um bem por unidade),
 * concluir manualmente ou descartar.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PendenciaPatrimoniamentoService {

    private static final String ENT = IntegracaoAlmoxarifadoService.ENT;
    private static final TypeReference<List<ItemPendencia>> LISTA_ITENS = new TypeReference<>() {};

    private final PendenciaPatrimoniamentoRepository repo;
    private final PatrimonioRepository patrimonioRepo;
    private final PatrimonioService patrimonioService;
    private final AnexoService anexoService;
    private final StorageService storageService;
    private final AuditoriaService auditoriaService;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public long contarPendentes() {
        return repo.countByStatus(StatusPendencia.PENDENTE);
    }

    @Transactional(readOnly = true)
    public Page<PendenciaResponse> listar(StatusPendencia status, Pageable pageable) {
        Page<PendenciaPatrimoniamento> pagina = status == null
                ? repo.findAll(pageable)
                : repo.findByStatusOrderByCriadoEmDesc(status, pageable);
        return pagina.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PendenciaResponse buscar(Long id) {
        return toResponse(carregar(id));
    }

    /** Recurso da NF para download — ou exceção se a pendência não tiver NF. */
    @Transactional(readOnly = true)
    public NotaFiscalParaDownload notaFiscal(Long id) {
        PendenciaPatrimoniamento p = carregar(id);
        if (!p.possuiNotaFiscal()) {
            throw new RecursoNaoEncontradoException("Nota fiscal da pendência", id);
        }
        Resource recurso = storageService.carregar(p.getNfCaminho());
        return new NotaFiscalParaDownload(p.getNfNomeOriginal(), p.getNfContentType(), recurso);
    }

    public record NotaFiscalParaDownload(String nomeOriginal, String contentType, Resource recurso) {}

    // ------------------------------------------------------------------ pré-preenchimento

    /**
     * Monta o {@link PatrimonioRequest} inicial para o formulário de "Novo patrimônio"
     * a partir dos dados da pendência. Lotação, responsável, tombo e categoria ficam
     * para o operador.
     *
     * <p>Com vários itens / quantidade &gt; 1, cada chamada aponta para a próxima
     * unidade ainda não patrimoniada (descrição e valor unitário daquele item).</p>
     */
    @Transactional(readOnly = true)
    public PatrimonioRequest prepararCadastro(Long id) {
        PendenciaPatrimoniamento p = carregar(id);
        if (!p.isPendente()) {
            throw new RegraDeNegocioException("Pendência #" + id + " já está " + p.getStatus() + ".");
        }
        List<ItemPendencia> itens = itensDe(p);
        ItemPendencia atual = itemDaProximaUnidade(itens, p.getQuantidadePatrimoniada());

        String descricao = atual != null && atual.descricao() != null
                ? atual.descricao().trim()
                : (p.getAssunto() != null ? p.getAssunto() : "Bem recebido do Almoxarifado");
        if (descricao.length() > 255) descricao = descricao.substring(0, 255);

        BigDecimal valor = valorDaUnidade(p, itens, atual);
        LocalDate dataCompra = p.getDataCompra() != null
                ? p.getDataCompra()
                : (p.getDataRecebimento() != null ? p.getDataRecebimento().toLocalDate() : LocalDate.now());
        if (dataCompra.isAfter(LocalDate.now())) dataCompra = LocalDate.now();

        return new PatrimonioRequest(
                null,                       // tombo — operador
                descricao,
                null, null,                 // categoria / subcategoria — operador
                dataCompra,
                valor,
                null,                       // conservação
                truncar(p.getNumeroNotaFiscal(), 60),
                null, null,                 // valor recuperável / impairment
                truncar(observacaoPadrao(p, atual), 1000),
                null,
                null, null                  // lotação / responsável — operador
        );
    }

    // ------------------------------------------------------------------ ações

    /**
     * Cria o patrimônio a partir do formulário, copia a NF da pendência como anexo
     * e avança o contador. Quando a última unidade é cadastrada, a pendência fica
     * CONCLUIDA automaticamente.
     *
     * @return o patrimônio criado
     */
    public PatrimonioResponse patrimoniar(Long pendenciaId, PatrimonioRequest request) {
        PendenciaPatrimoniamento p = carregar(pendenciaId);
        if (!p.isPendente()) {
            throw new RegraDeNegocioException("Pendência #" + pendenciaId + " já está " + p.getStatus() + ".");
        }

        PatrimonioResponse criado = patrimonioService.criar(request);

        if (p.possuiNotaFiscal()) {
            try {
                anexoService.anexarCopia(criado.id(), p.getNfCaminho(), p.getNfNomeOriginal(),
                        p.getNfContentType(), p.getNfTamanhoBytes(), TipoAnexo.NOTA_FISCAL);
            } catch (RuntimeException e) {
                // A NF continua disponível na pendência; não desfaz o cadastro do bem.
                log.warn("Não foi possível copiar a NF da pendência #{} para o patrimônio #{}: {}",
                        pendenciaId, criado.id(), e.getMessage());
            }
        }

        p.registrarPatrimoniado(patrimonioRepo.getReferenceById(criado.id()), usuarioAtual());

        auditoriaService.registrar(AcaoAuditoria.PATRIMONIAR, ENT, p.getId(),
                "Patrimônio #%d cadastrado a partir da pendência (%d/%d)%s".formatted(
                        criado.id(), p.getQuantidadePatrimoniada(), p.getQuantidadeTotal(),
                        p.isPendente() ? "" : " — pendência concluída"));
        return criado;
    }

    /** Conclui a pendência sem cadastrar as unidades restantes (decisão do operador). */
    public void concluirManualmente(Long id) {
        PendenciaPatrimoniamento p = carregar(id);
        if (!p.isPendente()) {
            throw new RegraDeNegocioException("Pendência #" + id + " já está " + p.getStatus() + ".");
        }
        p.concluir(usuarioAtual());
        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT, id,
                "Pendência concluída manualmente com %d de %d unidade(s) patrimoniada(s)".formatted(
                        p.getQuantidadePatrimoniada(), p.getQuantidadeTotal()));
    }

    /** Descarta a pendência (não gera patrimônio). Restrito a administradores na camada web. */
    public void descartar(Long id, String motivo) {
        PendenciaPatrimoniamento p = carregar(id);
        if (!p.isPendente()) {
            throw new RegraDeNegocioException("Pendência #" + id + " já está " + p.getStatus() + ".");
        }
        p.descartar(usuarioAtual());
        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT, id,
                "Pendência descartada" + (motivo != null && !motivo.isBlank() ? ": " + motivo.trim() : ""));
    }

    // ------------------------------------------------------------------ helpers

    private PendenciaPatrimoniamento carregar(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pendência de patrimoniamento", id));
    }

    private PendenciaResponse toResponse(PendenciaPatrimoniamento p) {
        return PendenciaResponse.from(p, itensDe(p));
    }

    private List<ItemPendencia> itensDe(PendenciaPatrimoniamento p) {
        if (p.getItensJson() == null || p.getItensJson().isBlank()) return List.of();
        try {
            return objectMapper.readValue(p.getItensJson(), LISTA_ITENS);
        } catch (Exception e) {
            log.warn("itens_json ilegível na pendência #{}: {}", p.getId(), e.getMessage());
            return List.of();
        }
    }

    /** Expande os itens por quantidade e devolve o que corresponde à unidade de índice {@code posicao}. */
    static ItemPendencia itemDaProximaUnidade(List<ItemPendencia> itens, int posicao) {
        if (itens.isEmpty()) return null;
        List<ItemPendencia> expandido = new ArrayList<>();
        for (ItemPendencia it : itens) {
            int q = it.quantidade() == null || it.quantidade() < 1 ? 1 : it.quantidade();
            for (int i = 0; i < q; i++) expandido.add(it);
        }
        if (posicao < 0 || posicao >= expandido.size()) return expandido.get(expandido.size() - 1);
        return expandido.get(posicao);
    }

    private static BigDecimal valorDaUnidade(PendenciaPatrimoniamento p, List<ItemPendencia> itens, ItemPendencia atual) {
        if (atual != null && atual.valorUnitario() != null) return atual.valorUnitario();
        if (p.getValorTotal() == null) return null;
        // Sem valor unitário: divide o total pela quantidade (1 unidade → total).
        int qtd = Math.max(1, p.getQuantidadeTotal());
        return qtd == 1 ? p.getValorTotal()
                        : p.getValorTotal().divide(BigDecimal.valueOf(qtd), 2, RoundingMode.HALF_UP);
    }

    private static String observacaoPadrao(PendenciaPatrimoniamento p, ItemPendencia atual) {
        List<String> partes = new ArrayList<>();
        partes.add("Origem: Almoxarifado compra #" + p.getCompraIdOrigem());
        if (p.getRetiradoPor() != null)     partes.add("Retirado por " + p.getRetiradoPor());
        if (p.getSetorDestino() != null)    partes.add("Setor " + p.getSetorDestino()
                + (p.getSetorDestinoCentroCusto() != null ? " (" + p.getSetorDestinoCentroCusto() + ")" : ""));
        if (p.getNumeroDocumento() != null) partes.add("Parte/Ofício " + p.getNumeroDocumento());
        if (p.getNumeroSgd() != null)       partes.add("SGD " + p.getNumeroSgd());
        if (p.getFornecedor() != null)      partes.add("Fornecedor " + p.getFornecedor());
        if (atual != null && atual.codigoSku() != null && !atual.codigoSku().isBlank())
            partes.add("SKU " + atual.codigoSku());
        if (p.getQuantidadeTotal() > 1)
            partes.add("Unidade " + (p.getQuantidadePatrimoniada() + 1) + " de " + p.getQuantidadeTotal());
        return partes.stream().collect(Collectors.joining(" · "));
    }

    private static String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "SYSTEM";
        }
        return auth.getName();
    }
}

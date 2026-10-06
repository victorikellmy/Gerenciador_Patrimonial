package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.ArquivoAnexo;
import com.fundacao.gerenciador_patrimonial.domain.entity.ItemLevantamento;
import com.fundacao.gerenciador_patrimonial.domain.entity.LevantamentoPatrimonial;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.domain.enums.ResultadoConferencia;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusLevantamento;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoAnexo;
import com.fundacao.gerenciador_patrimonial.dto.request.ConferenciaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.MovimentacaoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.AnexoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.ItemLevantamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.LevantamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.LocalLevantamentoResumo;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.ArquivoAnexoRepository;
import com.fundacao.gerenciador_patrimonial.repository.ItemLevantamentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.LevantamentoPatrimonialRepository;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Levantamento patrimonial (inventário físico anual).
 *
 * <p>Fluxo: {@link #abrir} congela todos os bens não baixados como itens, cada
 * um no local em que estava; o operador percorre o guia por local e chama
 * {@link #conferir} para cada bem (OK / avariado / não localizado, conservação,
 * foto); bens achados em outra sala entram via {@link #localizarPorTombo};
 * {@link #concluir} encerra a campanha.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LevantamentoService {

    private static final String ENT_LEV = "LevantamentoPatrimonial";
    private static final String ENT_PAT = "Patrimonio";

    private final LevantamentoPatrimonialRepository levantamentoRepo;
    private final ItemLevantamentoRepository itemRepo;
    private final PatrimonioRepository patrimonioRepo;
    private final LotacaoRepository lotacaoRepo;
    private final ArquivoAnexoRepository anexoRepo;
    private final AnexoService anexoService;
    private final PatrimonioService patrimonioService;
    private final AuditoriaService auditoriaService;

    // =========================================================================
    // Ciclo de vida do levantamento
    // =========================================================================

    /**
     * Abre um novo levantamento e gera um item para cada bem não baixado.
     * Só pode existir um levantamento ABERTO por vez.
     */
    public LevantamentoResponse abrir(int ano, String descricao, String observacao) {
        if (levantamentoRepo.existsByStatus(StatusLevantamento.ABERTO)) {
            throw new RegraDeNegocioException(
                    "Já existe um levantamento em andamento. Conclua-o antes de abrir outro.");
        }
        if (ano < 2000 || ano > 2100) {
            throw new RegraDeNegocioException("Ano inválido.");
        }
        String desc = nullIfBlank(descricao);
        if (desc == null) desc = "Levantamento patrimonial " + ano;

        List<Patrimonio> bens = patrimonioRepo.listarNaoBaixadosComLotacao();
        if (bens.isEmpty()) {
            throw new RegraDeNegocioException("Não há bens ativos para conferir.");
        }

        LevantamentoPatrimonial lev = levantamentoRepo.save(LevantamentoPatrimonial.builder()
                .ano(ano)
                .descricao(desc)
                .observacao(nullIfBlank(observacao))
                .totalItens(bens.size())
                .build());

        List<ItemLevantamento> itens = new ArrayList<>(bens.size());
        for (Patrimonio p : bens) {
            itens.add(ItemLevantamento.builder()
                    .levantamento(lev)
                    .patrimonio(p)
                    .lotacao(p.getLotacao())
                    .conservacaoAnterior(p.getConservacao())
                    .build());
        }
        itemRepo.saveAll(itens);

        auditoriaService.registrar(AcaoAuditoria.CREATE, ENT_LEV, lev.getId(),
                "Levantamento aberto: %s (%d bens)".formatted(desc, bens.size()));
        return toResponse(lev);
    }

    /** Encerra o levantamento. Itens não conferidos permanecem PENDENTE como registro. */
    public LevantamentoResponse concluir(Long id) {
        LevantamentoPatrimonial lev = buscarEntidade(id);
        lev.concluir(usuarioAtual());
        LevantamentoResponse r = toResponse(lev);
        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT_LEV, id,
                "Levantamento concluído: %d/%d conferidos, %d OK, %d avariados, %d não localizados, %d em local divergente"
                        .formatted(r.conferidos(), r.total(), r.ok(), r.avariados(),
                                   r.naoLocalizados(), r.divergenciasLocal()));
        return r;
    }

    /** Reabre um levantamento concluído (apenas se não houver outro em andamento). */
    public LevantamentoResponse reabrir(Long id) {
        if (levantamentoRepo.existsByStatus(StatusLevantamento.ABERTO)) {
            throw new RegraDeNegocioException("Já existe um levantamento em andamento.");
        }
        LevantamentoPatrimonial lev = buscarEntidade(id);
        lev.reabrir();
        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT_LEV, id, "Levantamento reaberto");
        return toResponse(lev);
    }

    // =========================================================================
    // Consultas
    // =========================================================================

    @Transactional(readOnly = true)
    public List<LevantamentoResponse> listar() {
        return levantamentoRepo.findAllByOrderByAbertoEmDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LevantamentoResponse buscar(Long id) {
        return toResponse(buscarEntidade(id));
    }

    /** Levantamento em andamento, se houver — alimenta badge e alerta do dashboard. */
    @Transactional(readOnly = true)
    public Optional<LevantamentoResponse> emAndamento() {
        return levantamentoRepo.findFirstByStatus(StatusLevantamento.ABERTO).map(this::toResponse);
    }

    /**
     * Guia por local: lotações agrupadas por UPM (ordem alfabética), cada uma
     * com os totais de conferência.
     */
    @Transactional(readOnly = true)
    public Map<String, List<LocalLevantamentoResumo>> guiaPorUpm(Long levantamentoId) {
        buscarEntidade(levantamentoId);

        Map<Long, long[]> encontrados = new HashMap<>();
        for (Object[] row : itemRepo.contarEncontradosForaDoEsperado(levantamentoId)) {
            encontrados.put((Long) row[0], new long[]{(Long) row[1]});
        }

        // Acumula (lotacaoId -> contadores) preservando a ordem upm, nome da query.
        Map<Long, Object[]> acumulado = new LinkedHashMap<>();
        for (Object[] row : itemRepo.resumoPorLocal(levantamentoId)) {
            Long lotId = (Long) row[0];
            Object[] acc = acumulado.computeIfAbsent(lotId, k ->
                    new Object[]{row[1], row[2], new EnumMap<ResultadoConferencia, Long>(ResultadoConferencia.class)});
            @SuppressWarnings("unchecked")
            Map<ResultadoConferencia, Long> mapa = (Map<ResultadoConferencia, Long>) acc[2];
            mapa.merge((ResultadoConferencia) row[3], (Long) row[4], Long::sum);
        }

        Map<String, List<LocalLevantamentoResumo>> porUpm = new LinkedHashMap<>();
        for (Map.Entry<Long, Object[]> e : acumulado.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<ResultadoConferencia, Long> mapa = (Map<ResultadoConferencia, Long>) e.getValue()[2];
            long pend = mapa.getOrDefault(ResultadoConferencia.PENDENTE, 0L);
            long ok   = mapa.getOrDefault(ResultadoConferencia.OK, 0L);
            long av   = mapa.getOrDefault(ResultadoConferencia.AVARIADO, 0L);
            long nl   = mapa.getOrDefault(ResultadoConferencia.NAO_LOCALIZADO, 0L);
            long enc  = encontrados.containsKey(e.getKey()) ? encontrados.get(e.getKey())[0] : 0L;
            String upm = (String) e.getValue()[0];
            porUpm.computeIfAbsent(upm, k -> new ArrayList<>())
                  .add(new LocalLevantamentoResumo(e.getKey(), upm, (String) e.getValue()[1],
                                                   pend + ok + av + nl, pend, ok, av, nl, enc));
        }
        return porUpm;
    }

    /** Checklist de um local: bens esperados ali + bens de outros locais achados ali. */
    @Transactional(readOnly = true)
    public List<ItemLevantamentoResponse> itensDoLocal(Long levantamentoId, Long lotacaoId) {
        buscarEntidade(levantamentoId);
        return comFotoAtual(itemRepo.listarDoLocal(levantamentoId, lotacaoId));
    }

    /** Itens com avaria, não localizados ou em local divergente. */
    @Transactional(readOnly = true)
    public List<ItemLevantamentoResponse> ocorrencias(Long levantamentoId) {
        buscarEntidade(levantamentoId);
        return comFotoAtual(itemRepo.listarOcorrencias(levantamentoId));
    }

    /** Todos os itens (para exportação), ordenados por local esperado. */
    @Transactional(readOnly = true)
    public List<ItemLevantamentoResponse> todosOsItens(Long levantamentoId) {
        buscarEntidade(levantamentoId);
        return comFotoAtual(itemRepo.listarTodosParaExportacao(levantamentoId));
    }

    @Transactional(readOnly = true)
    public Lotacao lotacao(Long lotacaoId) {
        return lotacaoRepo.findById(lotacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lotação", lotacaoId));
    }

    // =========================================================================
    // Conferência
    // =========================================================================

    /**
     * Registra a conferência de um item. Quando há foto, ela é anexada ao bem
     * (tipo FOTO) e vinculada ao item; a conservação informada atualiza o bem.
     *
     * @return o item atualizado
     */
    public ItemLevantamentoResponse conferir(Long levantamentoId,
                                             Long itemId,
                                             ConferenciaRequest req,
                                             MultipartFile foto) {
        LevantamentoPatrimonial lev = buscarEntidade(levantamentoId);
        lev.exigirAberto();
        ItemLevantamento item = buscarItem(levantamentoId, itemId);
        Patrimonio bem = item.getPatrimonio();

        Lotacao localEncontrado = null;
        if (req.lotacaoContextoId() != null
                && !req.lotacaoContextoId().equals(item.getLotacao().getId())) {
            localEncontrado = lotacao(req.lotacaoContextoId());
        } else if (item.isLocalDivergente()) {
            // Reconferência pela tela do local esperado mantém o "encontrado em" já registrado.
            localEncontrado = item.getLotacaoEncontrada();
        }

        item.conferir(req.resultado(), req.conservacaoVerificada(), req.descricaoAvaria(),
                      req.observacao(), localEncontrado, usuarioAtual());

        if (req.resultado() != ResultadoConferencia.NAO_LOCALIZADO) {
            bem.atualizarConservacao(req.conservacaoVerificada());
        }

        if (foto != null && !foto.isEmpty()) {
            if (foto.getContentType() == null || !foto.getContentType().startsWith("image/")) {
                throw new RegraDeNegocioException("A foto deve ser uma imagem (JPEG, PNG ou WEBP).");
            }
            AnexoResponse anexo = anexoService.anexar(bem.getId(), foto, TipoAnexo.FOTO);
            ArquivoAnexo ref = anexoRepo.getReferenceById(anexo.id());
            item.setFoto(ref);
        }

        StringBuilder desc = new StringBuilder("Levantamento %d: %s".formatted(lev.getAno(), req.resultado().getRotulo()));
        if (req.conservacaoVerificada() != null) desc.append("; conservação=").append(req.conservacaoVerificada());
        if (item.getDescricaoAvaria() != null)    desc.append("; avaria=").append(item.getDescricaoAvaria());
        if (item.isLocalDivergente())             desc.append("; encontrado em ").append(item.getLotacaoEncontrada().getNome());
        if (item.getFoto() != null)               desc.append("; foto atualizada");
        auditoriaService.registrar(AcaoAuditoria.LEVANTAMENTO, ENT_PAT, bem.getId(), desc.toString());

        return ItemLevantamentoResponse.from(item, fotoAtualDe(bem.getId()));
    }

    /** Volta um item para PENDENTE. A foto já anexada ao bem permanece como anexo. */
    public ItemLevantamentoResponse desfazer(Long levantamentoId, Long itemId) {
        buscarEntidade(levantamentoId).exigirAberto();
        ItemLevantamento item = buscarItem(levantamentoId, itemId);
        item.desfazerConferencia();
        auditoriaService.registrar(AcaoAuditoria.LEVANTAMENTO, ENT_PAT, item.getPatrimonio().getId(),
                "Conferência desfeita (voltou a pendente)");
        return ItemLevantamentoResponse.from(item, fotoAtualDe(item.getPatrimonio().getId()));
    }

    /**
     * Operador encontrou, no local {@code lotacaoId}, um bem que não estava na
     * lista daquele local. Localiza o item pelo tombo (ou ID) e marca onde foi
     * achado, deixando-o pronto para ser conferido ali.
     *
     * @return o item, agora visível no checklist de {@code lotacaoId}
     */
    public ItemLevantamentoResponse localizarPorTombo(Long levantamentoId, Long lotacaoId, String tombo) {
        buscarEntidade(levantamentoId).exigirAberto();
        String chave = nullIfBlank(tombo);
        if (chave == null) throw new RegraDeNegocioException("Informe o número de tombo do bem.");

        Patrimonio bem = patrimonioRepo.findByNumeroTombo(chave)
                .or(() -> {
                    String digitos = chave.replaceFirst("^#", "");
                    if (!digitos.matches("\\d{1,18}")) return Optional.empty();
                    return patrimonioRepo.findById(Long.parseLong(digitos));
                })
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Nenhum bem com tombo/ID '%s' foi encontrado.".formatted(chave)));

        ItemLevantamento item = itemRepo.findByLevantamentoIdAndPatrimonioId(levantamentoId, bem.getId())
                .orElseThrow(() -> new RegraDeNegocioException(
                        "O bem '%s' não faz parte deste levantamento (foi cadastrado depois da abertura ou está baixado)."
                                .formatted(bem.getDescricao())));

        if (item.getLotacao().getId().equals(lotacaoId)) {
            throw new RegraDeNegocioException(
                    "O bem '%s' já pertence a este local — procure-o na lista abaixo.".formatted(bem.getDescricao()));
        }
        Lotacao aqui = lotacao(lotacaoId);
        item.setLotacaoEncontrada(aqui);
        if (item.getResultado() == ResultadoConferencia.NAO_LOCALIZADO) {
            // Estava dado como perdido e apareceu em outra sala: volta a pendente para ser conferido.
            item.desfazerConferencia();
            item.setLotacaoEncontrada(aqui);
        }
        auditoriaService.registrar(AcaoAuditoria.LEVANTAMENTO, ENT_PAT, bem.getId(),
                "Encontrado em %s / %s (esperado em %s / %s)".formatted(
                        aqui.getUpm(), aqui.getNome(), item.getLotacao().getUpm(), item.getLotacao().getNome()));
        return ItemLevantamentoResponse.from(item, fotoAtualDe(bem.getId()));
    }

    /**
     * Regulariza uma divergência: move o bem para a lotação em que foi
     * encontrado (gera {@code Movimentacao} normalmente).
     */
    public void movimentarParaLocalEncontrado(Long levantamentoId, Long itemId) {
        LevantamentoPatrimonial lev = buscarEntidade(levantamentoId);
        ItemLevantamento item = buscarItem(levantamentoId, itemId);
        if (!item.isLocalDivergente()) {
            throw new RegraDeNegocioException("Este bem não está em local divergente.");
        }
        Lotacao destino = item.getLotacaoEncontrada();
        patrimonioService.movimentar(item.getPatrimonio().getId(), new MovimentacaoRequest(
                destino.getId(), null,
                "Levantamento %d: bem encontrado em %s / %s".formatted(lev.getAno(), destino.getUpm(), destino.getNome())));
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private LevantamentoResponse toResponse(LevantamentoPatrimonial lev) {
        Map<ResultadoConferencia, Long> porResultado = new EnumMap<>(ResultadoConferencia.class);
        for (Object[] row : itemRepo.contarPorResultado(lev.getId())) {
            porResultado.put((ResultadoConferencia) row[0], (Long) row[1]);
        }
        return LevantamentoResponse.from(lev, porResultado, itemRepo.contarDivergenciasDeLocal(lev.getId()));
    }

    private List<ItemLevantamentoResponse> comFotoAtual(List<ItemLevantamento> itens) {
        List<Long> ids = itens.stream().map(i -> i.getPatrimonio().getId()).toList();
        Map<Long, Long> fotoAtual = new HashMap<>();
        if (!ids.isEmpty()) {
            // Ordenado por criadoEm desc → o primeiro visto por bem é o mais recente.
            for (ArquivoAnexo a : anexoRepo.findByPatrimonioIdInAndTipoOrderByCriadoEmDesc(ids, TipoAnexo.FOTO)) {
                fotoAtual.putIfAbsent(a.getPatrimonio().getId(), a.getId());
            }
        }
        return itens.stream().map(i -> ItemLevantamentoResponse.from(i, fotoAtual.get(i.getPatrimonio().getId()))).toList();
    }

    private Long fotoAtualDe(Long patrimonioId) {
        return anexoRepo.findByPatrimonioIdInAndTipoOrderByCriadoEmDesc(List.of(patrimonioId), TipoAnexo.FOTO)
                .stream().findFirst().map(ArquivoAnexo::getId).orElse(null);
    }

    private LevantamentoPatrimonial buscarEntidade(Long id) {
        return levantamentoRepo.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Levantamento", id));
    }

    private ItemLevantamento buscarItem(Long levantamentoId, Long itemId) {
        return itemRepo.findByIdAndLevantamentoId(itemId, levantamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item do levantamento", itemId));
    }

    private static String usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "SYSTEM";
        }
        return auth.getName();
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

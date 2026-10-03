package com.fundacao.gerenciador_patrimonial.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundacao.gerenciador_patrimonial.domain.entity.PendenciaPatrimoniamento;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusPendencia;
import com.fundacao.gerenciador_patrimonial.dto.request.RecebimentoAlmoxarifadoRequest;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.PendenciaPatrimoniamentoRepository;
import com.fundacao.gerenciador_patrimonial.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Recebe os recebimentos de compras patrimoniais enviados pelo Almoxarifado e
 * os transforma em {@link PendenciaPatrimoniamento}.
 *
 * <p><b>Idempotência:</b> o Almoxarifado mantém uma fila (outbox) e reenvia o
 * mesmo recebimento enquanto não obtiver sucesso. O par
 * {@code (origem, compraId)} identifica o recebimento; se já existir, a
 * pendência existente é devolvida sem alterações.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegracaoAlmoxarifadoService {

    public static final String ENT = "PendenciaPatrimoniamento";
    private static final String TIPO_ANEXO_NF = "NOTA_FISCAL";
    /** Limite do PDF da NF (o mesmo do upload web: 10 MB). */
    private static final long NF_TAMANHO_MAX = 10L * 1024 * 1024;

    private final PendenciaPatrimoniamentoRepository repo;
    private final StorageService storageService;
    private final AuditoriaService auditoriaService;
    private final ObjectMapper objectMapper;

    /** Resultado do recebimento: a pendência e se ela foi criada nesta chamada. */
    public record Recebido(PendenciaPatrimoniamento pendencia, boolean criada) {}

    @Transactional(readOnly = true)
    public Optional<PendenciaPatrimoniamento> buscarExistente(String origem, Long compraId) {
        return repo.findByOrigemAndCompraIdOrigem(origem, compraId);
    }

    @Transactional
    public Recebido receber(RecebimentoAlmoxarifadoRequest req) {
        String origem = req.origemOuPadrao();

        Optional<PendenciaPatrimoniamento> existente = repo.findByOrigemAndCompraIdOrigem(origem, req.compraId());
        if (existente.isPresent()) {
            log.info("Recebimento {}#{} já registrado como pendência #{} — reenvio ignorado.",
                    origem, req.compraId(), existente.get().getId());
            return new Recebido(existente.get(), false);
        }

        List<RecebimentoAlmoxarifadoRequest.Item> itens = req.itens() != null ? req.itens() : List.of();
        int quantidadeTotal = Math.max(1, itens.stream()
                .mapToInt(RecebimentoAlmoxarifadoRequest.Item::quantidadeOuUm).sum());

        PendenciaPatrimoniamento p = PendenciaPatrimoniamento.builder()
                .origem(origem)
                .compraIdOrigem(req.compraId())
                .status(StatusPendencia.PENDENTE)
                .numeroDocumento(req.numeroDocumento())
                .numeroSgd(req.numeroSgd())
                .assunto(req.assunto())
                .solicitanteDocumento(req.solicitanteDocumento())
                .fornecedor(req.fornecedor())
                .numeroNotaFiscal(req.numeroNotaFiscal())
                .dataRecebimento(req.dataRecebimento())
                .dataCompra(req.dataCompra())
                .valorTotal(req.valorTotal())
                .retiradoPor(req.retiradoPor())
                .setorDestino(req.setorDestino())
                .setorDestinoCentroCusto(req.setorDestinoCentroCusto())
                .registradoPor(req.registradoPor())
                .observacao(req.observacao())
                .itensJson(serializar(itens))
                .quantidadeTotal(quantidadeTotal)
                .build();

        // Salva primeiro para obter o id (usado na subpasta do storage).
        p = repo.save(p);

        Optional<RecebimentoAlmoxarifadoRequest.Anexo> nf = anexoNotaFiscal(req);
        if (nf.isPresent()) {
            gravarNotaFiscal(p, nf.get());
        }

        auditoriaService.registrar(AcaoAuditoria.RECEBER_INTEGRACAO, ENT, p.getId(),
                "Recebido do %s: compra #%d, %s, NF %s, %d item(ns), setor %s".formatted(
                        origem, req.compraId(),
                        req.fornecedor() != null ? req.fornecedor() : "fornecedor não informado",
                        req.numeroNotaFiscal() != null ? req.numeroNotaFiscal() : "—",
                        quantidadeTotal,
                        req.setorDestino() != null ? req.setorDestino() : "—"));

        log.info("Pendência de patrimoniamento #{} criada a partir de {}#{}.", p.getId(), origem, req.compraId());
        return new Recebido(p, true);
    }

    // ------------------------------------------------------------------ helpers

    private static Optional<RecebimentoAlmoxarifadoRequest.Anexo> anexoNotaFiscal(RecebimentoAlmoxarifadoRequest req) {
        if (req.anexos() == null) return Optional.empty();
        return req.anexos().stream()
                .filter(a -> a != null
                        && TIPO_ANEXO_NF.equalsIgnoreCase(a.tipo())
                        && a.conteudoBase64() != null
                        && !a.conteudoBase64().isBlank())
                .findFirst();
    }

    private void gravarNotaFiscal(PendenciaPatrimoniamento p, RecebimentoAlmoxarifadoRequest.Anexo nf) {
        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(nf.conteudoBase64());
        } catch (IllegalArgumentException e) {
            throw new RegraDeNegocioException("conteudoBase64 da nota fiscal inválido.");
        }
        if (bytes.length == 0) {
            throw new RegraDeNegocioException("Nota fiscal vazia.");
        }
        if (bytes.length > NF_TAMANHO_MAX) {
            throw new RegraDeNegocioException("Nota fiscal excede o limite de 10 MB.");
        }

        String nome = nf.nomeOriginal() != null && !nf.nomeOriginal().isBlank()
                ? nf.nomeOriginal() : "nota-fiscal-" + p.getCompraIdOrigem() + ".pdf";
        String caminho = storageService.armazenar(
                new ByteArrayInputStream(bytes), nome, "pendencias/" + p.getId());

        p.setNfNomeOriginal(nome);
        p.setNfContentType(nf.contentType() != null ? nf.contentType() : "application/pdf");
        p.setNfTamanhoBytes((long) bytes.length);
        p.setNfCaminho(caminho);
    }

    private String serializar(List<RecebimentoAlmoxarifadoRequest.Item> itens) {
        try {
            String json = objectMapper.writeValueAsString(itens);
            if (json.length() > PendenciaPatrimoniamento.ITENS_JSON_MAX) {
                throw new RegraDeNegocioException("Lista de itens grande demais (máx. "
                        + PendenciaPatrimoniamento.ITENS_JSON_MAX + " caracteres em JSON).");
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new RegraDeNegocioException("Não foi possível interpretar os itens recebidos.");
        }
    }
}

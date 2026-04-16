package com.fundacao.gerenciador_patrimonial.service.importer;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Importador da planilha original "Planilha Parcial de Patrimônio.xlsx".
 *
 * <p>Estratégia de transação: <b>uma transação por linha</b>, via
 * {@link TransactionTemplate} com propagation default. Uma linha ruim
 * (constraint violation, dado inválido) só aborta a própria linha — as
 * demais continuam. Isso é essencial em planilhas reais que chegam sujas.</p>
 *
 * <p>Mapeamento das colunas:</p>
 * <pre>
 *   A (0) Unidade (UPM)
 *   B (1) Responsável
 *   C (2) Sala
 *   D (3) Nº Patrimônio
 *   E (4) Ativo (descrição)
 *   F (5) Categoria
 *   G (6) Data de Aquisição
 *   H (7) Custo Original
 *   J (9) Estado de Conservação
 *   R (17) NF
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportService {

    // Índices de coluna (0-based)
    private static final int COL_UPM         = 0;
    private static final int COL_RESP        = 1;
    private static final int COL_SALA        = 2;
    private static final int COL_TOMBO       = 3;
    private static final int COL_DESCRICAO   = 4;
    private static final int COL_CATEGORIA   = 5;
    private static final int COL_DATA_COMPRA = 6;
    private static final int COL_VALOR       = 7;
    private static final int COL_CONSERVACAO = 9;
    private static final int COL_NF          = 17;

    private final LotacaoRepository lotacaoRepo;
    private final ResponsavelRepository responsavelRepo;
    private final PatrimonioRepository patrimonioRepo;
    private final PlatformTransactionManager transactionManager;

    /** Template inicializado após a injeção do {@link PlatformTransactionManager}. */
    private TransactionTemplate txTemplate;

    @PostConstruct
    void initTxTemplate() {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Executa a importação. Cada linha é persistida em uma transação própria:
     * uma linha ruim NÃO aborta as demais.
     *
     * @param inputStream stream do .xlsx (não é fechado aqui — responsabilidade do chamador)
     * @param nomeSheet   nome da aba; se {@code null}, usa a primeira
     */
    public ImportResult importar(InputStream inputStream, String nomeSheet) throws IOException {
        int total = 0, importados = 0, ignorados = 0;
        List<String> erros = new ArrayList<>();

        // Caches e controle de duplicatas entre linhas do mesmo import
        Map<String, Long> cacheLotacaoId = new HashMap<>();    // chave "upm|nome" → id
        Map<String, Long> cacheRespId    = new HashMap<>();    // nome → id
        Set<String> tombosVistos         = new HashSet<>();    // evita tombo duplicado dentro da planilha
        int[] lotacoesCriadas     = {0};
        int[] responsaveisCriados = {0};

        try (Workbook wb = WorkbookFactory.create(inputStream)) {
            Sheet sheet = (nomeSheet != null) ? wb.getSheet(nomeSheet) : wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Aba não encontrada: " + nomeSheet);
            }

            boolean primeira = true;
            for (Row row : sheet) {
                if (primeira) { primeira = false; continue; } // pula cabeçalho
                if (isLinhaVazia(row)) { ignorados++; continue; }
                total++;

                final int numeroLinha = row.getRowNum() + 1;
                try {
                    ResultadoLinha r = txTemplate.execute(status ->
                            processarLinha(row, cacheLotacaoId, cacheRespId, tombosVistos));
                    if (r != null) {
                        if (r.lotacaoCriada)     lotacoesCriadas[0]++;
                        if (r.responsavelCriado) responsaveisCriados[0]++;
                        if (r.ignorado) {
                            ignorados++;
                        } else {
                            importados++;
                        }
                    }
                } catch (Exception e) {
                    erros.add("Linha %d: %s".formatted(numeroLinha, rootMessage(e)));
                    log.warn("Falha na linha {}: {}", numeroLinha, rootMessage(e));
                }
            }
        }

        log.info("Importação concluída: {}/{} linhas importadas, {} ignoradas. {} lotações e {} responsáveis novos.",
                importados, total, ignorados, lotacoesCriadas[0], responsaveisCriados[0]);
        return new ImportResult(total, importados, ignorados, erros, lotacoesCriadas[0], responsaveisCriados[0]);
    }

    // =========================================================================
    // Processamento de uma linha (executado dentro de uma transação própria)
    // =========================================================================

    /** DTO interno para retornar flags de "criou novo registro" e "ignorado". */
    private record ResultadoLinha(boolean lotacaoCriada, boolean responsavelCriado, boolean ignorado) {
        static ResultadoLinha ok(boolean loc, boolean resp) { return new ResultadoLinha(loc, resp, false); }
        static ResultadoLinha skip() { return new ResultadoLinha(false, false, true); }
    }

    private ResultadoLinha processarLinha(Row row,
                                          Map<String, Long> cacheLotacaoId,
                                          Map<String, Long> cacheRespId,
                                          Set<String> tombosVistos) {
        String upm       = Normalizadores.normalizarUpm(CellReader.lerString(row, COL_UPM));
        String sala      = Normalizadores.normalizarSala(CellReader.lerString(row, COL_SALA));
        String respNome  = Normalizadores.normalizarNome(CellReader.lerString(row, COL_RESP));
        String tombo     = Normalizadores.normalizarTombo(CellReader.lerString(row, COL_TOMBO));
        String descricao = CellReader.lerString(row, COL_DESCRICAO);
        String categoria = Normalizadores.normalizarNome(CellReader.lerString(row, COL_CATEGORIA));
        LocalDate data   = CellReader.lerData(row, COL_DATA_COMPRA);
        BigDecimal valor = CellReader.lerBigDecimal(row, COL_VALOR);
        String consRaw   = CellReader.lerString(row, COL_CONSERVACAO);
        String nf        = CellReader.lerString(row, COL_NF);

        if (descricao == null) {
            throw new IllegalArgumentException("Descrição vazia.");
        }
        if (upm == null || sala == null) {
            throw new IllegalArgumentException("UPM ou Sala não informadas.");
        }
        if (respNome == null) {
            throw new IllegalArgumentException("Responsável não informado.");
        }

        // --- Deduplicação de tombo ---
        if (tombo != null) {
            if (!tombosVistos.add(tombo) || patrimonioRepo.findByNumeroTombo(tombo).isPresent()) {
                log.debug("Tombo '{}' já presente — linha {} ignorada.", tombo, row.getRowNum() + 1);
                return ResultadoLinha.skip();
            }
        }

        // --- Lotação (upsert com cache) ---
        String chaveLotacao = upm + "|" + sala;
        boolean lotacaoCriada = false;
        Long lotacaoId = cacheLotacaoId.get(chaveLotacao);
        Lotacao lotacao;
        if (lotacaoId == null) {
            var existente = lotacaoRepo.findByUpmAndNome(upm, sala);
            if (existente.isPresent()) {
                lotacao = existente.get();
            } else {
                lotacao = lotacaoRepo.save(novaLotacao(upm, sala));
                lotacaoCriada = true;
            }
            cacheLotacaoId.put(chaveLotacao, lotacao.getId());
        } else {
            lotacao = lotacaoRepo.getReferenceById(lotacaoId);
        }

        // --- Responsável (upsert com cache) ---
        boolean responsavelCriado = false;
        Long respId = cacheRespId.get(respNome);
        Responsavel responsavel;
        if (respId == null) {
            var existente = responsavelRepo.findByNomeCompleto(respNome);
            if (existente.isPresent()) {
                responsavel = existente.get();
            } else {
                responsavel = responsavelRepo.save(novoResponsavel(respNome, lotacao));
                responsavelCriado = true;
            }
            cacheRespId.put(respNome, responsavel.getId());
        } else {
            responsavel = responsavelRepo.getReferenceById(respId);
        }

        // --- Patrimônio ---
        Patrimonio patrimonio = Patrimonio.builder()
                .numeroTombo(tombo)
                .descricao(descricao)
                .categoria(categoria)
                .dataCompra(data)
                .valorCompra(valor)
                .notaFiscal(nf)
                .lotacao(lotacao)
                .responsavel(responsavel)
                .build();

        resolverConservacaoESituacao(patrimonio, consRaw);
        patrimonioRepo.save(patrimonio);

        return ResultadoLinha.ok(lotacaoCriada, responsavelCriado);
    }

    /**
     * Valores "CAUTELADO" e "TECNICO" na coluna de conservação não são estados
     * físicos — são <b>situações administrativas</b>. Isolamos aqui.
     */
    private void resolverConservacaoESituacao(Patrimonio p, String consRaw) {
        Conservacao c = Conservacao.fromPlanilha(consRaw);
        if (c != null) {
            p.setConservacao(c);
            p.setSituacao(SituacaoPatrimonio.ATIVO);
        } else if (consRaw != null && consRaw.trim().equalsIgnoreCase("CAUTELADO")) {
            p.setSituacao(SituacaoPatrimonio.CAUTELADO);
        } else {
            // TECNICO, vazio, valor desconhecido — precisa revisão manual
            p.setSituacao(SituacaoPatrimonio.EM_APURACAO);
        }
    }

    private boolean isLinhaVazia(Row row) {
        return CellReader.lerString(row, COL_UPM) == null
                && CellReader.lerString(row, COL_DESCRICAO) == null
                && CellReader.lerString(row, COL_TOMBO) == null;
    }

    private Lotacao novaLotacao(String upm, String nome) {
        return Lotacao.builder()
                .upm(upm)
                .nome(nome)
                .tipoLocal(TipoLocal.INTERNO)   // default seguro; ajustável depois pela tela
                .build();
    }

    private Responsavel novoResponsavel(String nome, Lotacao lotacao) {
        return Responsavel.builder()
                .nomeCompleto(nome)
                .lotacao(lotacao)
                .ativo(true)
                .build();
    }

    /** Extrai a mensagem mais útil da cadeia de causas (para log de erro). */
    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        String msg = cur.getMessage();
        return msg != null ? msg : cur.getClass().getSimpleName();
    }
}

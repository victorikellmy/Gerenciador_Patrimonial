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
 * Importador da "Planilha de Reconstituição de Dados".
 *
 * <p>Estratégia de transação: <b>uma transação por linha</b>, via
 * {@link TransactionTemplate} com propagation default. Uma linha ruim
 * (constraint violation, dado inválido) só aborta a própria linha — as
 * demais continuam. Isso é essencial em planilhas reais que chegam sujas.</p>
 *
 * <p>Mapeamento das colunas (aba "Planilha1"):</p>
 * <pre>
 *   A (0)  Unidade (UPM)
 *   B (1)  Responsável
 *   C (2)  Sala
 *   D (3)  Nº Patrimônio
 *   E (4)  Ativo (descrição)
 *   F (5)  Categoria
 *   G (6)  DATA DE AQUISIÇÃO
 *   H (7)  Custo de Reposição / Custo Original
 *   I (8)  VUT Padrão (Anos)              — não persistido (validado contra vida_util_categoria)
 *   J (9)  Estado de Conservação
 *   K (10) VUD %                          — derivado, ignorado
 *   L (11) VUD (Anos)                     — derivado, ignorado
 *   M (12) VUR                            — derivado, ignorado
 *   N (13) Depreciação Acumulada          — derivado, ignorado
 *   O (14) VCL                            — derivado, ignorado
 *   P (15) Valor Recuperável (R$)
 *   Q (16) Perda por Impairment (R$)      — derivado, ignorado
 *   R (17) Nova Depreciação Anual (R$)    — derivado, ignorado
 *   S (18) Conclusão_Impairment
 *   T (19) Observação
 *   U (20) links
 *   V (21) NF
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportService {

    private static final String SHEET_PADRAO = "Planilha1";

    // Índices de coluna (0-based)
    private static final int COL_UPM             = 0;
    private static final int COL_RESP            = 1;
    private static final int COL_SALA            = 2;
    private static final int COL_TOMBO           = 3;
    private static final int COL_DESCRICAO       = 4;
    private static final int COL_CATEGORIA       = 5;
    private static final int COL_DATA_COMPRA     = 6;
    private static final int COL_VALOR           = 7;
    private static final int COL_VUT             = 8;
    private static final int COL_CONSERVACAO     = 9;
    private static final int COL_VALOR_RECUP     = 15;
    private static final int COL_CONCLUSAO_IMP   = 18;
    private static final int COL_OBSERVACAO      = 19;
    private static final int COL_LINK            = 20;
    private static final int COL_NF              = 21;

    private final LotacaoRepository lotacaoRepo;
    private final ResponsavelRepository responsavelRepo;
    private final PatrimonioRepository patrimonioRepo;
    private final com.fundacao.gerenciador_patrimonial.repository.VidaUtilCategoriaRepository vutRepo;
    private final PlatformTransactionManager transactionManager;

    /** VUT por categoria, carregado uma vez para validar a coluna VUT da planilha. */
    private final Map<String, Integer> vutPorCategoria = new HashMap<>();

    /** Template inicializado após a injeção do {@link PlatformTransactionManager}. */
    private TransactionTemplate txTemplate;

    @PostConstruct
    void initTxTemplate() {
        this.txTemplate = new TransactionTemplate(transactionManager);
        vutRepo.findAll().forEach(v ->
                vutPorCategoria.put(v.getCategoria().toUpperCase(), v.getVutAnos()));
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
            Sheet sheet;
            if (nomeSheet != null) {
                sheet = wb.getSheet(nomeSheet);
            } else {
                sheet = wb.getSheet(SHEET_PADRAO);
                if (sheet == null) sheet = wb.getSheetAt(0);
            }
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
        String upm           = Normalizadores.normalizarUpm(CellReader.lerString(row, COL_UPM));
        String sala          = Normalizadores.normalizarSala(CellReader.lerString(row, COL_SALA));
        String respNome      = Normalizadores.normalizarNome(CellReader.lerString(row, COL_RESP));
        String tombo         = Normalizadores.normalizarTombo(CellReader.lerString(row, COL_TOMBO));
        String descricao     = CellReader.lerString(row, COL_DESCRICAO);
        String categoria     = Normalizadores.normalizarNome(CellReader.lerString(row, COL_CATEGORIA));
        LocalDate data       = CellReader.lerData(row, COL_DATA_COMPRA);
        BigDecimal valor     = CellReader.lerBigDecimal(row, COL_VALOR);
        BigDecimal vutLinha  = CellReader.lerBigDecimal(row, COL_VUT);
        String consRaw       = CellReader.lerString(row, COL_CONSERVACAO);
        BigDecimal valorRec  = CellReader.lerBigDecimal(row, COL_VALOR_RECUP);
        String conclusao     = CellReader.lerString(row, COL_CONCLUSAO_IMP);
        String observacao    = CellReader.lerString(row, COL_OBSERVACAO);
        String link          = CellReader.lerString(row, COL_LINK);
        String nf            = CellReader.lerString(row, COL_NF);

        // Validação: VUT da linha deve bater com a tabela de referência por categoria.
        // Divergência só gera warning — a fonte de verdade é vida_util_categoria.
        if (vutLinha != null && categoria != null) {
            Integer vutRef = vutPorCategoria.get(categoria.toUpperCase());
            if (vutRef != null && vutLinha.intValue() != vutRef) {
                log.warn("Linha {}: VUT da planilha ({}) difere do cadastro ({}) p/ categoria '{}'.",
                        row.getRowNum() + 1, vutLinha, vutRef, categoria);
            }
        }

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
        EstadoConservacao estado = EstadoConservacao.resolver(consRaw);
        Patrimonio patrimonio = Patrimonio.builder()
                .numeroTombo(tombo)
                .descricao(descricao)
                .categoria(categoria)
                .dataCompra(data)
                .valorCompra(valor)
                .conservacao(estado.conservacao())
                .situacao(estado.situacao())
                .notaFiscal(nf)
                .valorRecuperavel(valorRec)
                .conclusaoImpairment(truncar(conclusao, 255))
                .observacao(truncar(observacao, 1000))
                .linkReferencia(truncar(link, 2000))
                .lotacao(lotacao)
                .responsavel(responsavel)
                .build();
        patrimonioRepo.save(patrimonio);

        return ResultadoLinha.ok(lotacaoCriada, responsavelCriado);
    }

    /**
     * Resolve o par (conservação, situação) a partir do texto bruto da planilha.
     *
     * <p>Valores "CAUTELADO" e "TECNICO" na coluna de conservação não são
     * estados físicos — são <b>situações administrativas</b>. Pure function:
     * sem mutação, fácil de testar isoladamente.</p>
     */
    private record EstadoConservacao(Conservacao conservacao, SituacaoPatrimonio situacao) {

        static EstadoConservacao resolver(String consRaw) {
            Conservacao c = Conservacao.fromPlanilha(consRaw);
            if (c != null) {
                return new EstadoConservacao(c, SituacaoPatrimonio.ATIVO);
            }
            if (consRaw != null && consRaw.trim().equalsIgnoreCase("CAUTELADO")) {
                return new EstadoConservacao(null, SituacaoPatrimonio.CAUTELADO);
            }
            // TECNICO, vazio, valor desconhecido — precisa revisão manual
            return new EstadoConservacao(null, SituacaoPatrimonio.EM_APURACAO);
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

    /** Trunca string ao limite da coluna preservando null. */
    private static String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Extrai a mensagem mais útil da cadeia de causas (para log de erro). */
    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        String msg = cur.getMessage();
        return msg != null ? msg : cur.getClass().getSimpleName();
    }
}

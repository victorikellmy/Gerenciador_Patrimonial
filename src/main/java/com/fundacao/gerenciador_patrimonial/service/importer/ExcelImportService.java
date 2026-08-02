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
import com.fundacao.gerenciador_patrimonial.util.Textos;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * <p>Estratégia de transação: <b>uma transação por chunk de linhas</b>
 * ({@value #LINHAS_POR_TRANSACAO}), via {@link TransactionTemplate}. Se o
 * chunk falhar (constraint violation, dado inválido), ele é revertido e
 * reprocessado <b>linha a linha</b>, cada uma em transação própria — assim
 * uma linha ruim só aborta a si mesma, sem pagar o custo de 1 commit por
 * linha no caminho feliz. Isso é essencial em planilhas reais que chegam
 * sujas.</p>
 *
 * <p>Antes do loop, tombos, lotações e responsáveis existentes são
 * pré-carregados em memória (3 queries no total) — o processamento de linha
 * não faz nenhum SELECT.</p>
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

    /** Linhas por transação no caminho feliz — reduz N commits para N/100. */
    private static final int LINHAS_POR_TRANSACAO = 100;

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
    private final com.fundacao.gerenciador_patrimonial.service.DepreciacaoService depreciacaoService;
    private final PlatformTransactionManager transactionManager;

    /** Template inicializado após a injeção do {@link PlatformTransactionManager}. */
    private TransactionTemplate txTemplate;

    @PostConstruct
    void initTxTemplate() {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Executa a importação em chunks transacionais (ver Javadoc da classe).
     *
     * @param inputStream stream do .xlsx (não é fechado aqui — responsabilidade do chamador)
     * @param nomeSheet   nome da aba; se {@code null}, usa a primeira
     */
    public ImportResult importar(InputStream inputStream, String nomeSheet) throws IOException {
        // Copia para arquivo temporário: WorkbookFactory sobre File usa ZipFile
        // (acesso randômico) em vez de bufferizar o .xlsx inteiro em heap —
        // criar direto do InputStream custa ~10-50x o tamanho do arquivo em RAM.
        Path tmp = Files.createTempFile("import-", ".xlsx");
        try {
            Files.copy(inputStream, tmp, StandardCopyOption.REPLACE_EXISTING);
            try (Workbook wb = WorkbookFactory.create(tmp.toFile(), null, true)) {
                return importarWorkbook(wb, nomeSheet);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private ImportResult importarWorkbook(Workbook wb, String nomeSheet) {
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

        // Pré-carga dos dados existentes (3 queries) — o loop não consulta o banco.
        ContextoImport global = new ContextoImport();
        global.tombos.addAll(patrimonioRepo.findAllNumerosTombo());
        lotacaoRepo.findAll().forEach(l ->
                global.lotacoes.put(l.getUpm() + "|" + l.getNome(), l.getId()));
        responsavelRepo.findAll().forEach(r ->
                global.responsaveis.put(r.getNomeCompleto(), r.getId()));

        List<Row> linhas = new ArrayList<>();
        int ignorados = 0;
        boolean primeira = true;
        for (Row row : sheet) {
            if (primeira) { primeira = false; continue; } // pula cabeçalho
            if (isLinhaVazia(row)) { ignorados++; continue; }
            linhas.add(row);
        }

        int total = linhas.size();
        int importados = 0, lotacoesCriadas = 0, responsaveisCriados = 0;
        List<String> erros = new ArrayList<>();

        for (int i = 0; i < linhas.size(); i += LINHAS_POR_TRANSACAO) {
            List<Row> chunk = linhas.subList(i, Math.min(i + LINHAS_POR_TRANSACAO, linhas.size()));
            List<ResultadoLinha> resultados = new ArrayList<>(chunk.size());

            // Caminho feliz: chunk inteiro em uma transação. As adições aos caches
            // vão para o delta e só são absorvidas no global após o commit — se o
            // chunk reverter, os ids criados nele somem do banco E dos caches.
            ContextoImport delta = new ContextoImport();
            try {
                List<ResultadoLinha> rs = txTemplate.execute(status -> {
                    List<ResultadoLinha> acc = new ArrayList<>(chunk.size());
                    for (Row row : chunk) {
                        acc.add(processarLinha(row, global, delta));
                    }
                    return acc;
                });
                global.absorver(delta);
                if (rs != null) resultados.addAll(rs);
            } catch (Exception e) {
                // Fallback: reprocessa o chunk linha a linha, cada uma em transação
                // própria, para isolar a(s) linha(s) ruim(ns) sem perder as boas.
                for (Row row : chunk) {
                    ContextoImport deltaLinha = new ContextoImport();
                    try {
                        ResultadoLinha r = txTemplate.execute(status ->
                                processarLinha(row, global, deltaLinha));
                        global.absorver(deltaLinha);
                        if (r != null) resultados.add(r);
                    } catch (Exception ex) {
                        erros.add("Linha %d: %s".formatted(row.getRowNum() + 1, rootMessage(ex)));
                        log.warn("Falha na linha {}: {}", row.getRowNum() + 1, rootMessage(ex));
                    }
                }
            }

            for (ResultadoLinha r : resultados) {
                if (r == null) continue;
                if (r.lotacaoCriada)     lotacoesCriadas++;
                if (r.responsavelCriado) responsaveisCriados++;
                if (r.ignorado) {
                    ignorados++;
                } else {
                    importados++;
                }
            }
        }

        log.info("Importação concluída: {}/{} linhas importadas, {} ignoradas. {} lotações e {} responsáveis novos.",
                importados, total, ignorados, lotacoesCriadas, responsaveisCriados);
        return new ImportResult(total, importados, ignorados, erros, lotacoesCriadas, responsaveisCriados);
    }

    // =========================================================================
    // Processamento de uma linha (executado dentro de uma transação própria)
    // =========================================================================

    /** DTO interno para retornar flags de "criou novo registro" e "ignorado". */
    private record ResultadoLinha(boolean lotacaoCriada, boolean responsavelCriado, boolean ignorado) {
        static ResultadoLinha ok(boolean loc, boolean resp) { return new ResultadoLinha(loc, resp, false); }
        static ResultadoLinha skip() { return new ResultadoLinha(false, false, true); }
    }

    /**
     * Caches da importação: tombos existentes e ids de lotação/responsável por
     * chave natural. O par (global, delta) existe para que um rollback de chunk
     * também "reverta" os caches: adições feitas dentro da transação ficam no
     * delta e só são absorvidas no global após o commit.
     */
    private static final class ContextoImport {
        final Map<String, Long> lotacoes     = new HashMap<>(); // chave "upm|nome" → id
        final Map<String, Long> responsaveis = new HashMap<>(); // nome → id
        final Set<String> tombos             = new HashSet<>();

        void absorver(ContextoImport delta) {
            lotacoes.putAll(delta.lotacoes);
            responsaveis.putAll(delta.responsaveis);
            tombos.addAll(delta.tombos);
        }
    }

    private ResultadoLinha processarLinha(Row row, ContextoImport global, ContextoImport delta) {
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
        // Divergência só gera warning — a fonte de verdade é vida_util_categoria
        // (mapa único mantido pelo DepreciacaoService).
        if (vutLinha != null && categoria != null) {
            Integer vutRef = depreciacaoService.vutDaCategoria(categoria);
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

        // --- Deduplicação de tombo (Sets pré-carregados — sem SELECT por linha) ---
        if (tombo != null) {
            if (global.tombos.contains(tombo) || !delta.tombos.add(tombo)) {
                log.debug("Tombo '{}' já presente — linha {} ignorada.", tombo, row.getRowNum() + 1);
                return ResultadoLinha.skip();
            }
        }

        // --- Lotação (upsert contra caches pré-carregados) ---
        String chaveLotacao = upm + "|" + sala;
        boolean lotacaoCriada = false;
        Long lotacaoId = delta.lotacoes.get(chaveLotacao);
        if (lotacaoId == null) lotacaoId = global.lotacoes.get(chaveLotacao);
        Lotacao lotacao;
        if (lotacaoId == null) {
            lotacao = lotacaoRepo.save(novaLotacao(upm, sala));
            delta.lotacoes.put(chaveLotacao, lotacao.getId());
            lotacaoCriada = true;
        } else {
            lotacao = lotacaoRepo.getReferenceById(lotacaoId);
        }

        // --- Responsável (upsert contra caches pré-carregados) ---
        boolean responsavelCriado = false;
        Long respId = delta.responsaveis.get(respNome);
        if (respId == null) respId = global.responsaveis.get(respNome);
        Responsavel responsavel;
        if (respId == null) {
            responsavel = responsavelRepo.save(novoResponsavel(respNome, lotacao));
            delta.responsaveis.put(respNome, responsavel.getId());
            responsavelCriado = true;
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
                .conclusaoImpairment(Textos.truncar(conclusao, 255))
                .observacao(Textos.truncar(observacao, 1000))
                .linkReferencia(Textos.truncar(link, 2000))
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

    /** Extrai a mensagem mais útil da cadeia de causas (para log de erro). */
    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        String msg = cur.getMessage();
        return msg != null ? msg : cur.getClass().getSimpleName();
    }
}

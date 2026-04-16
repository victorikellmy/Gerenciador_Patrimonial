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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Importador da planilha original "Planilha Parcial de Patrimônio.xlsx".
 *
 * <p>Mapeamento das colunas (baseado na análise feita):</p>
 * <pre>
 *   A (0) Unidade (UPM)
 *   B (1) Responsável
 *   C (2) Sala
 *   D (3) Nº Patrimônio
 *   E (4) Ativo (descrição)
 *   F (5) Categoria
 *   G (6) Data de Aquisição
 *   H (7) Custo Original
 *   I..Q — derivadas, ignoradas
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

    /**
     * Executa a importação. Cada patrimônio é persistido em uma savepoint;
     * uma linha ruim não aborta as demais.
     *
     * @param inputStream stream do .xlsx (não é fechado aqui — responsabilidade do chamador)
     * @param nomeSheet   nome da aba; se {@code null}, usa a primeira
     */
    @Transactional
    public ImportResult importar(InputStream inputStream, String nomeSheet) throws IOException {
        int total = 0, importados = 0, ignorados = 0;
        List<String> erros = new ArrayList<>();

        // Caches locais — evitam ir ao banco para cada linha quando lotação/responsável repetem
        Map<String, Lotacao> cacheLotacao = new HashMap<>();
        Map<String, Responsavel> cacheResp = new HashMap<>();
        int lotacoesCriadas = 0, responsaveisCriados = 0;

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

                try {
                    ResultadoLinha r = processarLinha(row, cacheLotacao, cacheResp);
                    if (r.lotacaoCriada)     lotacoesCriadas++;
                    if (r.responsavelCriado) responsaveisCriados++;
                    importados++;
                } catch (Exception e) {
                    erros.add("Linha %d: %s".formatted(row.getRowNum() + 1, e.getMessage()));
                    log.warn("Falha na linha {}: {}", row.getRowNum() + 1, e.getMessage());
                }
            }
        }

        log.info("Importação concluída: {}/{} linhas. {} lotações e {} responsáveis novos.",
                importados, total, lotacoesCriadas, responsaveisCriados);
        return new ImportResult(total, importados, ignorados, erros, lotacoesCriadas, responsaveisCriados);
    }

    // =========================================================================
    // Processamento de uma linha
    // =========================================================================

    /** Pequeno DTO interno para retornar flags de "criou novo registro". */
    private record ResultadoLinha(boolean lotacaoCriada, boolean responsavelCriado) {}

    private ResultadoLinha processarLinha(Row row,
                                          Map<String, Lotacao> cacheLotacao,
                                          Map<String, Responsavel> cacheResp) {
        String upm       = Normalizadores.normalizarUpm(CellReader.lerString(row, COL_UPM));
        String sala      = Normalizadores.normalizarSala(CellReader.lerString(row, COL_SALA));
        String respNome  = Normalizadores.normalizarNome(CellReader.lerString(row, COL_RESP));
        String tombo     = CellReader.lerString(row, COL_TOMBO);
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

        // --- Lotação (upsert com cache) ---
        String chaveLotacao = upm + "|" + sala;
        boolean lotacaoCriada = !cacheLotacao.containsKey(chaveLotacao)
                && lotacaoRepo.findByUpmAndNome(upm, sala).isEmpty();

        Lotacao lotacao = cacheLotacao.computeIfAbsent(chaveLotacao, k ->
                lotacaoRepo.findByUpmAndNome(upm, sala)
                        .orElseGet(() -> lotacaoRepo.save(novaLotacao(upm, sala))));

        // --- Responsável (upsert com cache) ---
        boolean responsavelCriado = !cacheResp.containsKey(respNome)
                && responsavelRepo.findByNomeCompleto(respNome).isEmpty();

        Responsavel responsavel = cacheResp.computeIfAbsent(respNome, k ->
                responsavelRepo.findByNomeCompleto(respNome)
                        .orElseGet(() -> responsavelRepo.save(novoResponsavel(respNome, lotacao))));

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

        return new ResultadoLinha(lotacaoCriada, responsavelCriado);
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
}

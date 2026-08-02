package com.fundacao.gerenciador_patrimonial.service.importer;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes do importador: pré-carga de caches (sem SELECT por linha),
 * deduplicação de tombo e o fallback linha-a-linha quando um chunk falha.
 */
class ExcelImportServiceTest {

    private LotacaoRepository lotacaoRepo;
    private ResponsavelRepository responsavelRepo;
    private PatrimonioRepository patrimonioRepo;

    private ExcelImportService service;

    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        lotacaoRepo     = mock(LotacaoRepository.class);
        responsavelRepo = mock(ResponsavelRepository.class);
        patrimonioRepo  = mock(PatrimonioRepository.class);
        DepreciacaoService depreciacaoService = mock(DepreciacaoService.class);
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);

        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(depreciacaoService.vutDaCategoria(anyString())).thenReturn(null);

        // Pré-cargas: banco tem apenas o tombo DUP-1, nenhuma lotação/responsável.
        when(patrimonioRepo.findAllNumerosTombo()).thenReturn(List.of("DUP-1"));
        when(lotacaoRepo.findAll()).thenReturn(List.of());
        when(responsavelRepo.findAll()).thenReturn(List.of());

        when(lotacaoRepo.save(any(Lotacao.class))).thenAnswer(inv -> {
            Lotacao l = inv.getArgument(0);
            l.setId(ids.incrementAndGet());
            return l;
        });
        when(responsavelRepo.save(any(Responsavel.class))).thenAnswer(inv -> {
            Responsavel r = inv.getArgument(0);
            r.setId(ids.incrementAndGet());
            return r;
        });
        when(lotacaoRepo.getReferenceById(anyLong()))
                .thenAnswer(inv -> Lotacao.builder().id(inv.getArgument(0)).build());
        when(responsavelRepo.getReferenceById(anyLong()))
                .thenAnswer(inv -> Responsavel.builder().id(inv.getArgument(0)).build());
        when(patrimonioRepo.save(any(Patrimonio.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new ExcelImportService(lotacaoRepo, responsavelRepo, patrimonioRepo,
                depreciacaoService, tm);
        service.initTxTemplate();
    }

    /** Monta um .xlsx em memória com cabeçalho + linhas (UPM, RESP, SALA, TOMBO, DESC, CAT, VALOR). */
    private InputStream planilha(String[][] linhas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Planilha1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Unidade");

            int rowIdx = 1;
            for (String[] l : linhas) {
                Row r = sheet.createRow(rowIdx++);
                if (l[0] != null) r.createCell(0).setCellValue(l[0]);  // UPM
                if (l[1] != null) r.createCell(1).setCellValue(l[1]);  // Responsável
                if (l[2] != null) r.createCell(2).setCellValue(l[2]);  // Sala
                if (l[3] != null) r.createCell(3).setCellValue(l[3]);  // Tombo
                if (l[4] != null) r.createCell(4).setCellValue(l[4]);  // Descrição
                if (l[5] != null) r.createCell(5).setCellValue(l[5]);  // Categoria
                r.createCell(7).setCellValue(100.50);                  // Valor
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test
    @DisplayName("importa linhas válidas, deduplica tombo (banco e planilha) e reusa caches — sem SELECT por linha")
    void importaComDeduplicacao() throws Exception {
        ImportResult r = service.importar(planilha(new String[][]{
                {"1BPM", "João da Silva", "ADM", "T-1",   "Mesa de escritório", "MOVEIS"},
                {"1BPM", "João da Silva", "ADM", "DUP-1", "Cadeira",            "MOVEIS"}, // tombo já no banco
                {"1BPM", "João da Silva", "ADM", "T-1",   "Armário",            "MOVEIS"}, // duplicado na planilha
                {"1BPM", "João da Silva", "ADM", null,    "Ventilador",         "MOVEIS"}, // sem tombo → importa
        }), null);

        assertThat(r.total()).isEqualTo(4);
        assertThat(r.importados()).isEqualTo(2);
        assertThat(r.ignorados()).isEqualTo(2);
        assertThat(r.erros()).isEmpty();
        assertThat(r.lotacoesCriadas()).isEqualTo(1);      // "1 BPM|ADM" reusada entre as linhas
        assertThat(r.responsaveisCriados()).isEqualTo(1);

        verify(patrimonioRepo, times(2)).save(any(Patrimonio.class));
        // O loop não pode consultar o banco por linha — tudo vem das pré-cargas.
        verify(patrimonioRepo, never()).findByNumeroTombo(anyString());
        verify(lotacaoRepo, never()).findByUpmAndNome(anyString(), anyString());
        verify(responsavelRepo, never()).findByNomeCompleto(anyString());
    }

    @Test
    @DisplayName("chunk com linha ruim é reprocessado linha a linha — só a linha ruim falha")
    void fallbackIsolaLinhaRuim() throws Exception {
        when(patrimonioRepo.save(any(Patrimonio.class))).thenAnswer(inv -> {
            Patrimonio p = inv.getArgument(0);
            if ("RUIM".equals(p.getDescricao())) {
                throw new RuntimeException("violação simulada");
            }
            return p;
        });

        ImportResult r = service.importar(planilha(new String[][]{
                {"1BPM", "João", "ADM", "T-1", "OK-1", "MOVEIS"},
                {"1BPM", "João", "ADM", "T-2", "RUIM", "MOVEIS"},
                {"1BPM", "João", "ADM", "T-3", "OK-3", "MOVEIS"},
        }), null);

        assertThat(r.total()).isEqualTo(3);
        assertThat(r.importados()).isEqualTo(2);           // as boas sobrevivem ao rollback do chunk
        assertThat(r.erros()).hasSize(1);
        assertThat(r.erros().get(0)).contains("violação simulada");
    }

    @Test
    @DisplayName("linha sem UPM/Sala vira erro descritivo, sem abortar a importação")
    void linhaSemUpmGeraErro() throws Exception {
        ImportResult r = service.importar(planilha(new String[][]{
                {null, "João", "ADM", "T-1", "Mesa", "MOVEIS"},          // sem UPM
                {"1BPM", "João", "ADM", "T-2", "Cadeira", "MOVEIS"},     // válida
        }), null);

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.importados()).isEqualTo(1);
        assertThat(r.erros()).hasSize(1);
        assertThat(r.erros().get(0)).contains("UPM ou Sala");
    }
}

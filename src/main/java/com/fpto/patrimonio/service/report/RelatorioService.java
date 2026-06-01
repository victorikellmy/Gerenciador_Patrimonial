package com.fpto.patrimonio.service.report;

import com.fpto.patrimonio.domain.entity.Patrimonio;
import com.fpto.patrimonio.domain.entity.Responsavel;
import com.fpto.patrimonio.exception.RecursoNaoEncontradoException;
import com.fpto.patrimonio.repository.PatrimonioRepository;
import com.fpto.patrimonio.repository.ResponsavelRepository;
import com.fpto.patrimonio.service.report.exporter.CsvExporter;
import com.fpto.patrimonio.service.report.exporter.PdfExporter;
import com.fpto.patrimonio.service.report.exporter.XlsxExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Orquestra a geração de relatórios — delega a consulta ao repositório
 * e a formatação ao exporter correspondente.
 *
 * <p>Cada método write* recebe um {@link OutputStream} (tipicamente o do
 * HttpServletResponse) e é responsável por fechar o workbook/document,
 * mas <b>não</b> fecha o stream: quem abriu é responsável por fechar.</p>
 */
@Service
@RequiredArgsConstructor
public class RelatorioService {

    private final PatrimonioRepository patrimonioRepo;
    private final ResponsavelRepository responsavelRepo;

    private final CsvExporter csvExporter;
    private final XlsxExporter xlsxExporter;
    private final PdfExporter pdfExporter;

    // =========================================================================
    // Inventário completo
    // =========================================================================

    @Transactional(readOnly = true)
    public void inventarioCsv(OutputStream out) {
        csvExporter.exportarInventario(patrimonioRepo.listarTudoParaRelatorio(), out);
    }

    @Transactional(readOnly = true)
    public void inventarioXlsx(OutputStream out) throws IOException {
        xlsxExporter.exportarInventario(patrimonioRepo.listarTudoParaRelatorio(), out);
    }

    @Transactional(readOnly = true)
    public void inventarioPdf(OutputStream out) {
        pdfExporter.gerarInventario(patrimonioRepo.listarTudoParaRelatorio(), out);
    }

    // =========================================================================
    // Baixas
    // =========================================================================

    @Transactional(readOnly = true)
    public void baixasCsv(OutputStream out) {
        csvExporter.exportarInventario(patrimonioRepo.listarBaixados(), out);
    }

    @Transactional(readOnly = true)
    public void baixasXlsx(OutputStream out) throws IOException {
        xlsxExporter.exportarInventario(patrimonioRepo.listarBaixados(), out);
    }

    // =========================================================================
    // Termo de responsabilidade (por responsável)
    // =========================================================================

    @Transactional(readOnly = true)
    public void termoResponsabilidade(Long responsavelId, OutputStream out) {
        Responsavel resp = responsavelRepo.findById(responsavelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Responsável não encontrado: id=" + responsavelId));
        List<Patrimonio> bens = patrimonioRepo.listarAtivosDoResponsavel(responsavelId);
        pdfExporter.gerarTermoResponsabilidade(resp, bens, out);
    }

    // =========================================================================
    // Visualização em tela (listas para templates)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<Patrimonio> listarInventario() {
        return patrimonioRepo.listarTudoParaRelatorio();
    }

    @Transactional(readOnly = true)
    public List<Patrimonio> listarBaixados() {
        return patrimonioRepo.listarBaixados();
    }
}

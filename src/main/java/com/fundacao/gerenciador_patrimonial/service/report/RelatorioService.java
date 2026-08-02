package com.fundacao.gerenciador_patrimonial.service.report;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.dto.response.PatrimonioResponse;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.CsvExporter;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.LinhaInventario;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.PdfExporter;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.XlsxExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
 *
 * <p>Os métodos de export NÃO são {@code @Transactional} de propósito: a
 * consulta roda na transação curta do próprio repositório e a escrita no
 * stream acontece fora dela — uma transação envolvendo o método inteiro
 * seguraria a conexão JDBC até o último byte chegar ao cliente (com clientes
 * lentos e downloads simultâneos, isso esgota o pool). As queries usam
 * {@code join fetch}, então as entidades saem completas e podem ser lidas
 * detached pelos exporters.</p>
 */
@Service
@RequiredArgsConstructor
public class RelatorioService {

    private final PatrimonioRepository patrimonioRepo;
    private final ResponsavelRepository responsavelRepo;
    private final DepreciacaoService depreciacaoService;

    private final CsvExporter csvExporter;
    private final XlsxExporter xlsxExporter;
    private final PdfExporter pdfExporter;

    // =========================================================================
    // Inventário completo
    // =========================================================================

    public void inventarioCsv(OutputStream out) throws IOException {
        csvExporter.exportarInventario(linhasInventario(patrimonioRepo.listarTudoParaRelatorio()), out);
    }

    public void inventarioXlsx(OutputStream out) throws IOException {
        xlsxExporter.exportarInventario(linhasInventario(patrimonioRepo.listarTudoParaRelatorio()), out);
    }

    public void inventarioPdf(OutputStream out) {
        pdfExporter.gerarInventario(linhasInventario(patrimonioRepo.listarTudoParaRelatorio()), out);
    }

    // =========================================================================
    // Baixas
    // =========================================================================

    public void baixasCsv(OutputStream out) throws IOException {
        csvExporter.exportarInventario(linhasInventario(patrimonioRepo.listarBaixados()), out);
    }

    public void baixasXlsx(OutputStream out) throws IOException {
        xlsxExporter.exportarInventario(linhasInventario(patrimonioRepo.listarBaixados()), out);
    }

    /** Achata entidades no modelo comum dos exporters, calculando a depreciação 1x por item. */
    private List<LinhaInventario> linhasInventario(List<Patrimonio> lista) {
        return lista.stream()
                .map(p -> LinhaInventario.de(p, depreciacaoService.calcular(p)))
                .toList();
    }

    // =========================================================================
    // Termo de responsabilidade (por responsável)
    // =========================================================================

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

    /**
     * Página do inventário para a tela, com a depreciação já calculada no DTO —
     * o template não chama mais service por linha, e a base inteira deixou de
     * ser renderizada de uma vez.
     */
    @Transactional(readOnly = true)
    public Page<PatrimonioResponse> inventarioPagina(Pageable pageable) {
        return patrimonioRepo.findAll((Specification<Patrimonio>) null, pageable)
                .map(p -> PatrimonioResponse.from(p, depreciacaoService.calcular(p)));
    }

    @Transactional(readOnly = true)
    public List<Patrimonio> listarBaixados() {
        return patrimonioRepo.listarBaixados();
    }
}

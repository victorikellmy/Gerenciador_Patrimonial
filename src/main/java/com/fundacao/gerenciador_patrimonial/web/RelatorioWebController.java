package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.dto.response.ResponsavelResponse;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService;
import com.fundacao.gerenciador_patrimonial.service.ResponsavelService;
import com.fundacao.gerenciador_patrimonial.service.report.RelatorioService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * Controllers web de relatórios.
 *
 * <p>Duas categorias de endpoint:</p>
 * <ul>
 *   <li>Telas HTML (GET) — menu, tabela de inventário, lista de baixados,
 *       seleção de responsável para termo</li>
 *   <li>Downloads binários (GET com formato) — CSV/XLSX/PDF, streaming
 *       direto no HttpServletResponse</li>
 * </ul>
 */
@Controller
@RequestMapping("/relatorios")
@RequiredArgsConstructor
@Slf4j
public class RelatorioWebController {

    private final RelatorioService relatorioService;
    private final ResponsavelService responsavelService;
    private final DepreciacaoService depreciacaoService;

    // =========================================================================
    // Telas
    // =========================================================================

    /** Menu principal. */
    @GetMapping
    public String menu() {
        return "relatorios/index";
    }

    /** Inventário em tela, com botões de download. */
    @GetMapping("/inventario")
    public String inventario(Model model) {
        List<Patrimonio> lista = relatorioService.listarInventario();
        model.addAttribute("lista", lista);
        model.addAttribute("depreciacaoService", depreciacaoService); // para uso inline no template
        return "relatorios/inventario";
    }

    /** Relatório de baixas. */
    @GetMapping("/baixas")
    public String baixas(Model model) {
        model.addAttribute("lista", relatorioService.listarBaixados());
        return "relatorios/baixas";
    }

    /** Tela para escolher responsável e gerar termo. */
    @GetMapping("/termo-responsabilidade")
    public String termoForm(Model model) {
        List<ResponsavelResponse> resps = responsavelService.listar(
                PageRequest.of(0, 1000, Sort.by("nomeCompleto"))
        ).getContent();
        model.addAttribute("responsaveis", resps);
        return "relatorios/termo";
    }

    // =========================================================================
    // Downloads
    // =========================================================================

    @GetMapping(value = "/inventario/download")
    public void baixarInventario(@RequestParam(defaultValue = "csv") String formato,
                                 HttpServletResponse response) throws IOException {
        switch (formato.toLowerCase()) {
            case "xlsx" -> writeXlsx(response, "inventario", out -> {
                try { relatorioService.inventarioXlsx(out); } catch (IOException e) { throw new RuntimeException(e); }
            });
            case "pdf"  -> writePdf(response, "inventario",
                    out -> relatorioService.inventarioPdf(out));
            default     -> writeCsv(response, "inventario",
                    out -> relatorioService.inventarioCsv(out));
        }
    }

    @GetMapping(value = "/baixas/download")
    public void baixarBaixas(@RequestParam(defaultValue = "csv") String formato,
                             HttpServletResponse response) throws IOException {
        if ("xlsx".equalsIgnoreCase(formato)) {
            writeXlsx(response, "baixas", out -> {
                try { relatorioService.baixasXlsx(out); } catch (IOException e) { throw new RuntimeException(e); }
            });
        } else {
            writeCsv(response, "baixas", out -> relatorioService.baixasCsv(out));
        }
    }

    @GetMapping("/termo-responsabilidade/{responsavelId}")
    public void baixarTermo(@PathVariable Long responsavelId,
                            HttpServletResponse response) throws IOException {
        writePdf(response, "termo_responsabilidade_" + responsavelId,
                out -> relatorioService.termoResponsabilidade(responsavelId, out));
    }

    // =========================================================================
    // Helpers de download
    // =========================================================================

    @FunctionalInterface
    private interface StreamWriter {
        void write(OutputStream out) throws IOException;
    }

    private void writeCsv(HttpServletResponse response, String base, StreamWriter writer) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + nome(base, "csv") + "\"");
        writer.write(response.getOutputStream());
        response.flushBuffer();
    }

    private void writeXlsx(HttpServletResponse response, String base, StreamWriter writer) throws IOException {
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + nome(base, "xlsx") + "\"");
        writer.write(response.getOutputStream());
        response.flushBuffer();
    }

    private void writePdf(HttpServletResponse response, String base, StreamWriter writer) throws IOException {
        response.setContentType(MediaType.APPLICATION_PDF_VALUE);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + nome(base, "pdf") + "\"");
        writer.write(response.getOutputStream());
        response.flushBuffer();
    }

    private String nome(String base, String ext) {
        return base + "_" + LocalDate.now() + "." + ext;
    }
}

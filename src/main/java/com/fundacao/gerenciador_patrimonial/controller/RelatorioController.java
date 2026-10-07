package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.service.report.RelatorioService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

/** Downloads de relatórios pela API — mesmos arquivos do {@code RelatorioWebController}. */
@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final RelatorioService relatorioService;

    @GetMapping("/inventario")
    public void inventario(@RequestParam(defaultValue = "csv") String formato,
                           HttpServletResponse response) throws IOException {
        switch (formato.toLowerCase()) {
            case "xlsx" -> { preparar(response, XLSX, "inventario", "xlsx"); relatorioService.inventarioXlsx(response.getOutputStream()); }
            case "pdf"  -> { preparar(response, MediaType.APPLICATION_PDF_VALUE, "inventario", "pdf"); relatorioService.inventarioPdf(response.getOutputStream()); }
            default     -> { preparar(response, "text/csv; charset=UTF-8", "inventario", "csv"); relatorioService.inventarioCsv(response.getOutputStream()); }
        }
        response.flushBuffer();
    }

    @GetMapping("/baixas")
    public void baixas(@RequestParam(defaultValue = "csv") String formato,
                       HttpServletResponse response) throws IOException {
        if ("xlsx".equalsIgnoreCase(formato)) {
            preparar(response, XLSX, "baixas", "xlsx");
            relatorioService.baixasXlsx(response.getOutputStream());
        } else {
            preparar(response, "text/csv; charset=UTF-8", "baixas", "csv");
            relatorioService.baixasCsv(response.getOutputStream());
        }
        response.flushBuffer();
    }

    @GetMapping("/termo-responsabilidade/{responsavelId}")
    public void termo(@PathVariable Long responsavelId, HttpServletResponse response) throws IOException {
        preparar(response, MediaType.APPLICATION_PDF_VALUE, "termo_responsabilidade_" + responsavelId, "pdf");
        relatorioService.termoResponsabilidade(responsavelId, response.getOutputStream());
        response.flushBuffer();
    }

    private static void preparar(HttpServletResponse response, String contentType, String base, String ext) {
        response.setContentType(contentType);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + base + "_" + LocalDate.now() + "." + ext + "\"");
    }
}

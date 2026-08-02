package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.service.importer.ExcelImportService;
import com.fundacao.gerenciador_patrimonial.service.importer.ImportJobService;
import com.fundacao.gerenciador_patrimonial.service.importer.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Endpoints de importação de planilha.
 *
 * <ul>
 *   <li>{@code POST /patrimonios} — síncrono (compatibilidade com integrações
 *       existentes; adequado a planilhas pequenas).</li>
 *   <li>{@code POST /patrimonios/async} — dispara job em background e responde
 *       202 com o id; acompanhar em {@code GET /jobs/{id}}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/importacao")
@RequiredArgsConstructor
public class ImportacaoController {

    private final ExcelImportService importService;
    private final ImportJobService importJobService;

    @PostMapping(value = "/patrimonios", consumes = "multipart/form-data")
    public ImportResult importar(@RequestParam("arquivo") MultipartFile arquivo,
                                 @RequestParam(value = "aba", required = false) String aba) {
        validar(arquivo);
        try (InputStream is = arquivo.getInputStream()) {
            return importService.importar(is, aba);
        } catch (IOException e) {
            throw new RegraDeNegocioException("Falha ao ler planilha: " + e.getMessage());
        }
    }

    /** Versão assíncrona: responde imediatamente com o id do job (HTTP 202). */
    @PostMapping(value = "/patrimonios/async", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> importarAsync(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(value = "aba", required = false) String aba) {
        validar(arquivo);
        try {
            Path tmp = Files.createTempFile("upload-import-", ".xlsx");
            arquivo.transferTo(tmp);
            String jobId = importJobService.iniciar(tmp, aba, arquivo.getOriginalFilename());
            return ResponseEntity.accepted().body(Map.of(
                    "jobId", jobId,
                    "status", "/api/importacao/jobs/" + jobId));
        } catch (IOException e) {
            throw new RegraDeNegocioException("Falha ao receber planilha: " + e.getMessage());
        }
    }

    /** Estado/progresso de um job assíncrono. */
    @GetMapping("/jobs/{id}")
    public ImportJobService.StatusJob statusJob(@PathVariable String id) {
        return importJobService.status(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Job de importação não encontrado: " + id));
    }

    private static void validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Arquivo vazio.");
        }
        String nome = arquivo.getOriginalFilename();
        if (nome == null || !(nome.toLowerCase().endsWith(".xlsx") || nome.toLowerCase().endsWith(".xls"))) {
            throw new RegraDeNegocioException("Formato inválido. Envie um arquivo .xlsx ou .xls.");
        }
    }
}

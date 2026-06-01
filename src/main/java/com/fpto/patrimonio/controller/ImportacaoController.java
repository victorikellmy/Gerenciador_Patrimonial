package com.fpto.patrimonio.controller;

import com.fpto.patrimonio.exception.RegraDeNegocioException;
import com.fpto.patrimonio.service.importer.ExcelImportService;
import com.fpto.patrimonio.service.importer.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Endpoint para reimportação manual (upload de planilha atualizada).
 */
@RestController
@RequestMapping("/api/importacao")
@RequiredArgsConstructor
public class ImportacaoController {

    private final ExcelImportService importService;

    @PostMapping(value = "/patrimonios", consumes = "multipart/form-data")
    public ImportResult importar(@RequestParam("arquivo") MultipartFile arquivo,
                                 @RequestParam(value = "aba", required = false) String aba) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Arquivo vazio.");
        }
        String nome = arquivo.getOriginalFilename();
        if (nome == null || !(nome.toLowerCase().endsWith(".xlsx") || nome.toLowerCase().endsWith(".xls"))) {
            throw new RegraDeNegocioException("Formato inválido. Envie um arquivo .xlsx ou .xls.");
        }

        try (InputStream is = arquivo.getInputStream()) {
            return importService.importar(is, aba);
        } catch (IOException e) {
            throw new RegraDeNegocioException("Falha ao ler planilha: " + e.getMessage());
        }
    }
}

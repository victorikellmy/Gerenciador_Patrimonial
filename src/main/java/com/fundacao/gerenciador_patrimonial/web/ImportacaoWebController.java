package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.service.importer.ExcelImportService;
import com.fundacao.gerenciador_patrimonial.service.importer.ImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Interface web para a importação manual da planilha de patrimônio.
 *
 * <p>Diferente do endpoint REST {@code /api/importacao/*}, aqui o resultado
 * é renderizado em página HTML com as estatísticas e a lista de erros.</p>
 */
@Controller
@RequestMapping("/importacao")
@RequiredArgsConstructor
@Slf4j
public class ImportacaoWebController {

    private final ExcelImportService excelImportService;

    /** Tela inicial: formulário de upload. */
    @GetMapping
    public String form() {
        return "importacao/index";
    }

    /**
     * Processa o upload síncrono.
     * Para planilhas grandes seria melhor fazer async + job status,
     * mas para a primeira carga (506 linhas) é aceitável bloquear.
     */
    @PostMapping
    public String importar(@RequestParam("arquivo") MultipartFile arquivo,
                           @RequestParam(value = "sheet", required = false) String sheet,
                           Model model) {
        if (arquivo == null || arquivo.isEmpty()) {
            model.addAttribute("erro", "Selecione um arquivo .xlsx para importar.");
            return "importacao/index";
        }

        try {
            String nomeSheet = (sheet != null && !sheet.isBlank()) ? sheet.trim() : "val est cons";
            ImportResult resultado = excelImportService.importar(arquivo.getInputStream(), nomeSheet);
            model.addAttribute("resultado", resultado);
            model.addAttribute("arquivoNome", arquivo.getOriginalFilename());
            model.addAttribute("sheetUsada", nomeSheet);
            model.addAttribute("sucesso",
                    "Importação concluída: " + resultado.importados() + " / " + resultado.total() + " linhas.");
        } catch (IOException e) {
            log.error("Erro ao ler planilha", e);
            model.addAttribute("erro", "Falha ao ler o arquivo: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            model.addAttribute("erro", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Erro inesperado na importação", e);
            model.addAttribute("erro", "Erro inesperado: " + e.getMessage());
        }
        return "importacao/index";
    }
}

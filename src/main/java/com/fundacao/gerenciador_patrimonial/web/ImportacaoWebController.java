package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.service.importer.ImportJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Interface web para a importação da planilha de patrimônio.
 *
 * <p>O upload dispara um <b>job assíncrono</b> ({@link ImportJobService}) e
 * redireciona para a tela de status, que acompanha o progresso por polling.
 * Planilhas grandes não bloqueiam mais a request nem estouram o timeout
 * do navegador.</p>
 */
@Controller
@RequestMapping("/importacao")
@RequiredArgsConstructor
@Slf4j
public class ImportacaoWebController {

    private static final String SHEET_PADRAO = "val est cons";

    private final ImportJobService importJobService;

    /** Tela inicial: formulário de upload. */
    @GetMapping
    public String form() {
        return "importacao/index";
    }

    /** Recebe o upload, salva em arquivo temporário e dispara o job. */
    @PostMapping
    public String importar(@RequestParam("arquivo") MultipartFile arquivo,
                           @RequestParam(value = "sheet", required = false) String sheet,
                           RedirectAttributes redirect) {
        if (arquivo == null || arquivo.isEmpty()) {
            redirect.addFlashAttribute("erro", "Selecione um arquivo .xlsx para importar.");
            return "redirect:/importacao";
        }

        String nomeSheet = (sheet != null && !sheet.isBlank()) ? sheet.trim() : SHEET_PADRAO;
        try {
            // Cópia própria do upload: o temp do multipart é descartado no fim
            // da request, antes de o job assíncrono conseguir lê-lo.
            Path tmp = Files.createTempFile("upload-import-", ".xlsx");
            arquivo.transferTo(tmp);

            String jobId = importJobService.iniciar(tmp, nomeSheet, arquivo.getOriginalFilename());
            return "redirect:/importacao/status/" + jobId;
        } catch (IOException e) {
            log.error("Falha ao receber upload da planilha", e);
            redirect.addFlashAttribute("erro", "Falha ao receber o arquivo: " + e.getMessage());
            return "redirect:/importacao";
        }
    }

    /** Tela de acompanhamento: recarrega sozinha enquanto o job roda. */
    @GetMapping("/status/{id}")
    public String status(@PathVariable String id, Model model, RedirectAttributes redirect) {
        var status = importJobService.status(id).orElse(null);
        if (status == null) {
            redirect.addFlashAttribute("erro",
                    "Importação não encontrada (o histórico expira após algumas horas).");
            return "redirect:/importacao";
        }
        model.addAttribute("job", status);
        return "importacao/status";
    }
}

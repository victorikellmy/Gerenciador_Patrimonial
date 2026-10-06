package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.ResultadoConferencia;
import com.fundacao.gerenciador_patrimonial.dto.request.ConferenciaRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.ItemLevantamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.LevantamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.LocalLevantamentoResumo;
import com.fundacao.gerenciador_patrimonial.service.AnexoService;
import com.fundacao.gerenciador_patrimonial.service.LevantamentoService;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.LevantamentoExporter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Levantamento patrimonial (inventário físico anual).
 *
 * <ul>
 *   <li>{@code GET  /levantamentos} — campanhas abertas/concluídas + abrir nova (ADMIN)</li>
 *   <li>{@code GET  /levantamentos/{id}} — guia por UPM/local com progresso e ocorrências</li>
 *   <li>{@code GET  /levantamentos/{id}/locais/{lotacaoId}} — checklist do local</li>
 *   <li>{@code POST /levantamentos/{id}/itens/{itemId}/conferir} — OK / avaria / não localizado + foto</li>
 *   <li>{@code POST /levantamentos/{id}/locais/{lotacaoId}/localizar} — bem achado fora do local esperado</li>
 *   <li>{@code POST /levantamentos/{id}/concluir} — encerra (ADMIN)</li>
 *   <li>{@code GET  /levantamentos/{id}/download?formato=xlsx|pdf}</li>
 * </ul>
 */
@Controller
@RequestMapping("/levantamentos")
@RequiredArgsConstructor
public class LevantamentoWebController {

    private final LevantamentoService service;
    private final LevantamentoExporter exporter;
    private final AnexoService anexoService;

    // =========================================================================
    // Campanhas
    // =========================================================================

    @GetMapping
    public String listar(Model model) {
        List<LevantamentoResponse> lista = service.listar();
        model.addAttribute("levantamentos", lista);
        model.addAttribute("existeAberto", lista.stream().anyMatch(LevantamentoResponse::aberto));
        model.addAttribute("anoSugerido", Year.now().getValue());
        return "levantamentos/list";
    }

    @PostMapping
    public String abrir(@RequestParam int ano,
                        @RequestParam(required = false) String descricao,
                        @RequestParam(required = false) String observacao,
                        RedirectAttributes redirect) {
        try {
            LevantamentoResponse lev = service.abrir(ano, descricao, observacao);
            redirect.addFlashAttribute("sucesso",
                    "Levantamento aberto com " + lev.total() + " bem(ns) para conferir.");
            return "redirect:/levantamentos/" + lev.id();
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/levantamentos";
        }
    }

    @PostMapping("/{id}/concluir")
    public String concluir(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            LevantamentoResponse lev = service.concluir(id);
            redirect.addFlashAttribute("sucesso",
                    "Levantamento concluído: %d de %d bens conferidos.".formatted(lev.conferidos(), lev.total()));
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/levantamentos/" + id;
    }

    @PostMapping("/{id}/reabrir")
    public String reabrir(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.reabrir(id);
            redirect.addFlashAttribute("sucesso", "Levantamento reaberto.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/levantamentos/" + id;
    }

    // =========================================================================
    // Guia por local
    // =========================================================================

    @GetMapping("/{id}")
    public String guia(@PathVariable Long id, Model model) {
        LevantamentoResponse lev = service.buscar(id);
        Map<String, List<LocalLevantamentoResumo>> porUpm = service.guiaPorUpm(id);

        // Totais por UPM para o cabeçalho de cada grupo.
        Map<String, LocalLevantamentoResumo> totaisUpm = new LinkedHashMap<>();
        porUpm.forEach((upm, locais) -> {
            long total = 0, pend = 0, ok = 0, av = 0, nl = 0, enc = 0;
            for (LocalLevantamentoResumo l : locais) {
                total += l.total(); pend += l.pendentes(); ok += l.ok();
                av += l.avariados(); nl += l.naoLocalizados(); enc += l.encontradosAqui();
            }
            totaisUpm.put(upm, new LocalLevantamentoResumo(null, upm, null, total, pend, ok, av, nl, enc));
        });

        model.addAttribute("lev", lev);
        model.addAttribute("porUpm", porUpm);
        model.addAttribute("totaisUpm", totaisUpm);
        model.addAttribute("ocorrencias", service.ocorrencias(id));
        return "levantamentos/guia";
    }

    @GetMapping("/{id}/locais/{lotacaoId}")
    public String local(@PathVariable Long id,
                        @PathVariable Long lotacaoId,
                        @RequestParam(defaultValue = "false") boolean todos,
                        Model model) {
        LevantamentoResponse lev = service.buscar(id);
        Lotacao lotacao = service.lotacao(lotacaoId);
        List<ItemLevantamentoResponse> itens = service.itensDoLocal(id, lotacaoId);

        long pendentes = itens.stream().filter(ItemLevantamentoResponse::pendente).count();
        List<ItemLevantamentoResponse> exibidos = todos
                ? itens
                : itens.stream().filter(ItemLevantamentoResponse::pendente).toList();

        model.addAttribute("lev", lev);
        model.addAttribute("lotacao", lotacao);
        model.addAttribute("itens", exibidos);
        model.addAttribute("total", itens.size());
        model.addAttribute("pendentes", pendentes);
        model.addAttribute("todos", todos);
        model.addAttribute("conservacoes", Conservacao.values());
        model.addAttribute("resultados", ResultadoConferencia.values());
        return "levantamentos/local";
    }

    // =========================================================================
    // Conferência de itens
    // =========================================================================

    @PostMapping("/{id}/itens/{itemId}/conferir")
    public String conferir(@PathVariable Long id,
                           @PathVariable Long itemId,
                           @Valid @ModelAttribute ConferenciaRequest req,
                           BindingResult binding,
                           @RequestParam(value = "foto", required = false) MultipartFile foto,
                           @RequestParam(defaultValue = "false") boolean todos,
                           RedirectAttributes redirect) {
        Long lotacaoId = req.lotacaoContextoId();
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("erro", binding.getAllErrors().get(0).getDefaultMessage());
            return voltarParaLocal(id, lotacaoId, todos, itemId);
        }
        try {
            ItemLevantamentoResponse item = service.conferir(id, itemId, req, foto);
            redirect.addFlashAttribute("sucesso",
                    "%s: %s registrado.".formatted(item.descricao(), item.resultado().getRotulo()));
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return voltarParaLocal(id, lotacaoId, todos, itemId);
    }

    @PostMapping("/{id}/itens/{itemId}/desfazer")
    public String desfazer(@PathVariable Long id,
                           @PathVariable Long itemId,
                           @RequestParam Long lotacaoId,
                           @RequestParam(defaultValue = "false") boolean todos,
                           RedirectAttributes redirect) {
        try {
            service.desfazer(id, itemId);
            redirect.addFlashAttribute("sucesso", "Conferência desfeita — o bem voltou a pendente.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return voltarParaLocal(id, lotacaoId, todos, itemId);
    }

    @PostMapping("/{id}/locais/{lotacaoId}/localizar")
    public String localizar(@PathVariable Long id,
                            @PathVariable Long lotacaoId,
                            @RequestParam String tombo,
                            @RequestParam(defaultValue = "false") boolean todos,
                            RedirectAttributes redirect) {
        try {
            ItemLevantamentoResponse item = service.localizarPorTombo(id, lotacaoId, tombo);
            redirect.addFlashAttribute("sucesso",
                    "%s adicionado a este local (esperado em %s / %s). Registre a conferência."
                            .formatted(item.descricao(), item.lotacaoUpm(), item.lotacaoNome()));
            return voltarParaLocal(id, lotacaoId, todos, item.id());
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return voltarParaLocal(id, lotacaoId, todos, null);
        }
    }

    @PostMapping("/{id}/itens/{itemId}/movimentar")
    public String movimentar(@PathVariable Long id,
                             @PathVariable Long itemId,
                             @RequestParam(required = false) Long lotacaoId,
                             RedirectAttributes redirect) {
        try {
            service.movimentarParaLocalEncontrado(id, itemId);
            redirect.addFlashAttribute("sucesso", "Bem movimentado para o local em que foi encontrado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return lotacaoId != null ? voltarParaLocal(id, lotacaoId, true, itemId) : "redirect:/levantamentos/" + id;
    }

    // =========================================================================
    // Fotos e exportação
    // =========================================================================

    /** Exibe a foto inline (miniaturas do checklist). */
    @GetMapping("/fotos/{anexoId}")
    public ResponseEntity<Resource> foto(@PathVariable Long anexoId) {
        AnexoService.ArquivoParaDownload arq = anexoService.baixar(anexoId);
        MediaType tipo = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (arq.anexo().getContentType() != null) tipo = MediaType.parseMediaType(arq.anexo().getContentType());
        } catch (RuntimeException ignored) { /* mantém octet-stream */ }
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(arq.recurso());
    }

    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id,
                         @RequestParam(defaultValue = "xlsx") String formato,
                         HttpServletResponse response) throws IOException {
        LevantamentoResponse lev = service.buscar(id);
        List<ItemLevantamentoResponse> itens = service.todosOsItens(id);
        String base = "levantamento_" + lev.ano() + "_" + LocalDate.now();

        if ("pdf".equalsIgnoreCase(formato)) {
            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + base + ".pdf\"");
            exporter.pdf(lev, itens, response.getOutputStream());
        } else {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + base + ".xlsx\"");
            exporter.xlsx(lev, itens, response.getOutputStream());
        }
        response.flushBuffer();
    }

    // ------------------------------------------------------------------ helpers

    private String voltarParaLocal(Long levId, Long lotacaoId, boolean todos, Long itemId) {
        if (lotacaoId == null) return "redirect:/levantamentos/" + levId;
        StringBuilder url = new StringBuilder("redirect:/levantamentos/").append(levId)
                .append("/locais/").append(lotacaoId);
        if (todos) url.append("?todos=true");
        if (itemId != null) url.append("#item-").append(itemId);
        return url.toString();
    }
}

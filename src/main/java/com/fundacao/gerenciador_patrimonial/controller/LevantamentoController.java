package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.dto.request.AbrirLevantamentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.ConferenciaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.TomboRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.ItemLevantamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.LevantamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.UpmGuiaResponse;
import com.fundacao.gerenciador_patrimonial.service.LevantamentoService;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.LevantamentoExporter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * API REST do levantamento patrimonial (consumida pelo app Android).
 * Só delega ao {@link LevantamentoService}; as regras ficam lá.
 */
@RestController
@RequestMapping("/api/levantamentos")
@RequiredArgsConstructor
public class LevantamentoController {

    private final LevantamentoService service;
    private final LevantamentoExporter exporter;

    // ------------------------------------------------------------- campanhas

    @GetMapping
    public List<LevantamentoResponse> listar() {
        return service.listar();
    }

    @PostMapping
    public ResponseEntity<LevantamentoResponse> abrir(@Valid @RequestBody AbrirLevantamentoRequest req) {
        LevantamentoResponse lev = service.abrir(req.ano(), req.descricao(), req.observacao());
        return ResponseEntity.created(URI.create("/api/levantamentos/" + lev.id())).body(lev);
    }

    /** 200 com o levantamento aberto, ou 204 sem corpo quando não há nenhum em andamento. */
    @GetMapping("/em-andamento")
    public ResponseEntity<LevantamentoResponse> emAndamento() {
        return service.emAndamento()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public LevantamentoResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping("/{id}/concluir")
    public LevantamentoResponse concluir(@PathVariable Long id) {
        return service.concluir(id);
    }

    @PostMapping("/{id}/reabrir")
    public LevantamentoResponse reabrir(@PathVariable Long id) {
        return service.reabrir(id);
    }

    // ------------------------------------------------------------- guia e itens

    @GetMapping("/{id}/guia")
    public List<UpmGuiaResponse> guia(@PathVariable Long id) {
        return service.guiaPorUpm(id).entrySet().stream()
                .map(e -> new UpmGuiaResponse(e.getKey(), e.getValue()))
                .toList();
    }

    @GetMapping("/{id}/locais/{lotacaoId}/itens")
    public List<ItemLevantamentoResponse> itensDoLocal(@PathVariable Long id, @PathVariable Long lotacaoId) {
        return service.itensDoLocal(id, lotacaoId);
    }

    @GetMapping("/{id}/ocorrencias")
    public List<ItemLevantamentoResponse> ocorrencias(@PathVariable Long id) {
        return service.ocorrencias(id);
    }

    @GetMapping("/{id}/itens")
    public List<ItemLevantamentoResponse> itens(@PathVariable Long id) {
        return service.todosOsItens(id);
    }

    // ------------------------------------------------------------- conferência

    @PostMapping(value = "/{id}/itens/{itemId}/conferir", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemLevantamentoResponse conferir(@PathVariable Long id,
                                             @PathVariable Long itemId,
                                             @Valid @ModelAttribute ConferenciaRequest req,
                                             @RequestParam(value = "foto", required = false) MultipartFile foto) {
        return service.conferir(id, itemId, req, foto);
    }

    @PostMapping("/{id}/itens/{itemId}/desfazer")
    public ItemLevantamentoResponse desfazer(@PathVariable Long id, @PathVariable Long itemId) {
        return service.desfazer(id, itemId);
    }

    @PostMapping("/{id}/locais/{lotacaoId}/localizar")
    public ItemLevantamentoResponse localizar(@PathVariable Long id,
                                              @PathVariable Long lotacaoId,
                                              @Valid @RequestBody TomboRequest req) {
        return service.localizarPorTombo(id, lotacaoId, req.tombo());
    }

    @PostMapping("/{id}/itens/{itemId}/movimentar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void movimentar(@PathVariable Long id, @PathVariable Long itemId) {
        service.movimentarParaLocalEncontrado(id, itemId);
    }

    // ------------------------------------------------------------- exportação

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
}

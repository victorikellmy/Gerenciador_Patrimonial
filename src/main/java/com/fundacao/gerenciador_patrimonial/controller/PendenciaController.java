package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.domain.enums.StatusPendencia;
import com.fundacao.gerenciador_patrimonial.dto.request.MotivoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.PatrimonioResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.PendenciaResponse;
import com.fundacao.gerenciador_patrimonial.service.PendenciaPatrimoniamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** API REST das pendências de patrimoniamento recebidas do Almoxarifado. */
@RestController
@RequestMapping("/api/pendencias")
@RequiredArgsConstructor
public class PendenciaController {

    private final PendenciaPatrimoniamentoService service;

    @GetMapping
    public Page<PendenciaResponse> listar(@RequestParam(required = false) StatusPendencia status,
                                          Pageable pageable) {
        return service.listar(status, pageable);
    }

    @GetMapping("/contagem")
    public Map<String, Long> contagem() {
        return Map.of("pendentes", service.contarPendentes());
    }

    @GetMapping("/{id}")
    public PendenciaResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @GetMapping("/{id}/nota-fiscal")
    public ResponseEntity<Resource> notaFiscal(@PathVariable Long id) {
        var nf = service.notaFiscal(id);
        String nome = nf.nomeOriginal() != null ? nf.nomeOriginal() : "nota-fiscal.pdf";
        String nomeAscii = nome.replaceAll("[^\\x20-\\x7E]", "_");
        String nomeUtf8 = URLEncoder.encode(nome, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType tipo = MediaType.APPLICATION_OCTET_STREAM;
        try { if (nf.contentType() != null) tipo = MediaType.parseMediaType(nf.contentType()); }
        catch (RuntimeException ignored) { /* mantém octet-stream */ }

        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + nomeAscii + "\"; filename*=UTF-8''" + nomeUtf8)
                .body(nf.recurso());
    }

    @GetMapping("/{id}/preparar")
    public PatrimonioRequest preparar(@PathVariable Long id) {
        return service.prepararCadastro(id);
    }

    @PostMapping("/{id}/patrimoniar")
    public ResponseEntity<PatrimonioResponse> patrimoniar(@PathVariable Long id,
                                                          @Valid @RequestBody PatrimonioRequest req) {
        PatrimonioResponse criado = service.patrimoniar(id, req);
        return ResponseEntity.created(URI.create("/api/patrimonios/" + criado.id())).body(criado);
    }

    @PostMapping("/{id}/concluir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void concluir(@PathVariable Long id) {
        service.concluirManualmente(id);
    }

    @PostMapping("/{id}/descartar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descartar(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
        service.descartar(id, req.motivo());
    }
}

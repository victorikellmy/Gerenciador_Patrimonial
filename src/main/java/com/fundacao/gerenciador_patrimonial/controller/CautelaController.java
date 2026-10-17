package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.dto.request.CautelaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroFrota;
import com.fundacao.gerenciador_patrimonial.dto.request.FinalizarDeslocamentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.CautelaResponse;
import com.fundacao.gerenciador_patrimonial.service.CautelaService;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.DiariaPdfExporter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API REST das cautelas de veículo (SPA e app). */
@RestController
@RequestMapping("/api/frota/cautelas")
@RequiredArgsConstructor
public class CautelaController {

    private final CautelaService service;
    private final DiariaPdfExporter pdf;

    @GetMapping
    public Page<CautelaResponse> listar(@ModelAttribute FiltroFrota filtro, @PageableDefault(size = 25) Pageable pageable) {
        return service.listar(filtro, pageable);
    }

    @GetMapping("/em-aberto")
    public List<CautelaResponse> emAberto() { return service.emAberto(); }

    @GetMapping("/{id}")
    public CautelaResponse buscar(@PathVariable Long id) { return service.buscar(id); }

    @GetMapping("/{id}/termo")
    public ResponseEntity<byte[]> termo(@PathVariable Long id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"cautela-" + id + ".pdf\"")
                .body(pdf.termoCautela(service.buscar(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CautelaResponse criar(@Valid @RequestBody CautelaRequest req) { return service.criar(req); }

    @PutMapping("/{id}")
    public CautelaResponse atualizar(@PathVariable Long id, @Valid @RequestBody CautelaRequest req) { return service.atualizar(id, req); }

    @PostMapping("/{id}/finalizar")
    public CautelaResponse finalizar(@PathVariable Long id, @Valid @RequestBody FinalizarDeslocamentoRequest req) {
        return service.finalizar(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) { service.excluir(id); return ResponseEntity.noContent().build(); }
}

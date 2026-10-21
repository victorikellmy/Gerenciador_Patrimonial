package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.domain.entity.SeguroVeiculo;
import com.fundacao.gerenciador_patrimonial.dto.request.SeguroVeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.SinistroVeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.AlertasSeguroResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.SeguroVeiculoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.SinistroVeiculoResponse;
import com.fundacao.gerenciador_patrimonial.service.FrotaService;
import com.fundacao.gerenciador_patrimonial.service.SeguroVeiculoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seguro da frota (SPA e app). Consulta para quem vê a frota; cadastro e alteração só para
 * quem gere a frota (ADMIN/FISCAL/PRESIDENTE) e exclusão só ADMIN — regras na SecurityConfig.
 */
@RestController
@RequestMapping("/api/frota")
@RequiredArgsConstructor
public class SeguroController {

    private final SeguroVeiculoService service;

    // ------------------------------------------------------------------ apólices

    @GetMapping("/seguros")
    public List<SeguroVeiculoResponse> listar(@RequestParam(required = false) Long veiculoId,
                                              @RequestParam(required = false) SeguroVeiculo.Situacao situacao,
                                              @RequestParam(required = false) String texto) {
        return service.listar(veiculoId, situacao, texto);
    }

    @GetMapping("/seguros/alertas")
    public AlertasSeguroResponse alertas() { return service.alertas(); }

    @GetMapping("/seguros/{id}")
    public SeguroVeiculoResponse buscar(@PathVariable Long id) { return service.buscar(id); }

    @PostMapping("/seguros")
    @ResponseStatus(HttpStatus.CREATED)
    public SeguroVeiculoResponse criar(@Valid @RequestBody SeguroVeiculoRequest req) { return service.criar(req, null); }

    /** Variante multipart: parte "dados" (JSON) + "arquivo" (apólice em PDF ou foto, opcional). */
    @PostMapping(value = "/seguros", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SeguroVeiculoResponse criarComApolice(@Valid @RequestPart("dados") SeguroVeiculoRequest req,
                                                 @RequestPart(value = "arquivo", required = false) MultipartFile arquivo) {
        return service.criar(req, arquivo);
    }

    @PutMapping("/seguros/{id}")
    public SeguroVeiculoResponse atualizar(@PathVariable Long id, @Valid @RequestBody SeguroVeiculoRequest req) {
        return service.atualizar(id, req);
    }

    @PostMapping(value = "/seguros/{id}/apolice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SeguroVeiculoResponse anexarApolice(@PathVariable Long id, @RequestParam("arquivo") MultipartFile arquivo) {
        return service.anexarApolice(id, arquivo);
    }

    @GetMapping("/seguros/{id}/apolice")
    public ResponseEntity<Resource> apolice(@PathVariable Long id) {
        FrotaService.NotaFiscalArquivo a = service.apolice(id);
        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        try { if (a.contentType() != null) mt = MediaType.parseMediaType(a.contentType()); } catch (RuntimeException ignored) { }
        String nome = a.nomeOriginal() != null ? a.nomeOriginal() : "apolice-" + id;
        return ResponseEntity.ok().contentType(mt)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(nome, StandardCharsets.UTF_8).build().toString())
                .body(a.recurso());
    }

    @DeleteMapping("/seguros/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) { service.excluir(id); return ResponseEntity.noContent().build(); }

    // ------------------------------------------------------------------ sinistros

    @GetMapping("/sinistros")
    public List<SinistroVeiculoResponse> sinistros(@RequestParam(required = false) Long veiculoId) { return service.sinistros(veiculoId); }

    @GetMapping("/sinistros/{id}")
    public SinistroVeiculoResponse sinistro(@PathVariable Long id) { return service.buscarSinistro(id); }

    @PostMapping("/sinistros")
    @ResponseStatus(HttpStatus.CREATED)
    public SinistroVeiculoResponse criarSinistro(@Valid @RequestBody SinistroVeiculoRequest req) { return service.criarSinistro(req); }

    @PutMapping("/sinistros/{id}")
    public SinistroVeiculoResponse atualizarSinistro(@PathVariable Long id, @Valid @RequestBody SinistroVeiculoRequest req) {
        return service.atualizarSinistro(id, req);
    }

    @DeleteMapping("/sinistros/{id}")
    public ResponseEntity<Void> excluirSinistro(@PathVariable Long id) { service.excluirSinistro(id); return ResponseEntity.noContent().build(); }
}

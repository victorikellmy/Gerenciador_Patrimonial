package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.dto.request.LotacaoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.LotacaoResponse;
import com.fundacao.gerenciador_patrimonial.service.LotacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Endpoints REST para gestão de Lotações.
 * As restrições de perfil (ADMINISTRADOR/FISCAL) serão adicionadas na Sprint 5.
 */
@RestController
@RequestMapping("/api/lotacoes")
@RequiredArgsConstructor
public class LotacaoController {

    private final LotacaoService service;

    @PostMapping
    public ResponseEntity<LotacaoResponse> criar(@Valid @RequestBody LotacaoRequest request) {
        LotacaoResponse criada = service.criar(request);
        return ResponseEntity
                .created(URI.create("/api/lotacoes/" + criada.id()))
                .body(criada);
    }

    @GetMapping
    public Page<LotacaoResponse> listar(Pageable pageable) {
        return service.listar(pageable);
    }

    @GetMapping("/{id}")
    public LotacaoResponse buscar(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @GetMapping("/por-upm")
    public List<LotacaoResponse> porUpm(@RequestParam String upm) {
        return service.buscarPorUpm(upm);
    }

    @PutMapping("/{id}")
    public LotacaoResponse atualizar(@PathVariable Long id,
                                     @Valid @RequestBody LotacaoRequest request) {
        return service.atualizar(id, request);
    }

    @PutMapping("/{id}/responsavel/{novoResponsavelId}")
    public LotacaoResponse trocarResponsavel(@PathVariable Long id,
                                             @PathVariable Long novoResponsavelId) {
        return service.trocarResponsavelDoSetor(id, novoResponsavelId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}

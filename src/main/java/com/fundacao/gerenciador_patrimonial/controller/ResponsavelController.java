package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.dto.request.ResponsavelRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.ResponsavelResponse;
import com.fundacao.gerenciador_patrimonial.service.ResponsavelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/responsaveis")
@RequiredArgsConstructor
public class ResponsavelController {

    private final ResponsavelService service;

    @PostMapping
    public ResponseEntity<ResponsavelResponse> criar(@Valid @RequestBody ResponsavelRequest request) {
        ResponsavelResponse criado = service.criar(request);
        return ResponseEntity
                .created(URI.create("/api/responsaveis/" + criado.id()))
                .body(criado);
    }

    @GetMapping
    public Page<ResponsavelResponse> listar(Pageable pageable) {
        return service.listar(pageable);
    }

    @GetMapping("/{id}")
    public ResponsavelResponse buscar(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @GetMapping("/por-lotacao/{lotacaoId}")
    public List<ResponsavelResponse> porLotacao(@PathVariable Long lotacaoId) {
        return service.buscarPorLotacao(lotacaoId);
    }

    @PutMapping("/{id}")
    public ResponsavelResponse atualizar(@PathVariable Long id,
                                         @Valid @RequestBody ResponsavelRequest request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativar(@PathVariable Long id) {
        service.inativar(id);
    }
}

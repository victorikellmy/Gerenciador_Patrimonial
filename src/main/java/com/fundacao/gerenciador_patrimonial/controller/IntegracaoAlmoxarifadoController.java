package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.dto.request.RecebimentoAlmoxarifadoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.PendenciaIntegracaoResponse;
import com.fundacao.gerenciador_patrimonial.service.IntegracaoAlmoxarifadoService;
import com.fundacao.gerenciador_patrimonial.service.IntegracaoAlmoxarifadoService.Recebido;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint consumido pelo Almoxarifado (HTTP Basic, perfil INTEGRACAO ou ADMINISTRADOR).
 *
 * <pre>
 * POST /api/integracao/almoxarifado/recebimentos
 *   201 { id, status }  → pendência criada
 *   200 { id, status }  → compraId já recebido antes (idempotente)
 *   400 ErroResponse    → payload inválido
 * </pre>
 */
@RestController
@RequestMapping("/api/integracao/almoxarifado")
@RequiredArgsConstructor
@Slf4j
public class IntegracaoAlmoxarifadoController {

    private final IntegracaoAlmoxarifadoService service;

    @PostMapping("/recebimentos")
    public ResponseEntity<PendenciaIntegracaoResponse> receber(
            @Valid @RequestBody RecebimentoAlmoxarifadoRequest request) {
        Recebido r;
        try {
            r = service.receber(request);
        } catch (DataIntegrityViolationException e) {
            // Dois reenvios simultâneos do mesmo compraId: o segundo bate na unique.
            // Devolve a pendência que o primeiro criou.
            var existente = service.buscarExistente(request.origemOuPadrao(), request.compraId());
            if (existente.isEmpty()) throw e;
            log.info("Recebimento concorrente de {}#{} — devolvendo pendência #{}.",
                    request.origemOuPadrao(), request.compraId(), existente.get().getId());
            return ResponseEntity.ok(PendenciaIntegracaoResponse.from(existente.get()));
        }
        return ResponseEntity
                .status(r.criada() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(PendenciaIntegracaoResponse.from(r.pendencia()));
    }
}

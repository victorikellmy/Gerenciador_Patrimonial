package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.dto.request.TrocarSenhaRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.UsuarioResponse;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import com.fundacao.gerenciador_patrimonial.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Sessão do usuário autenticado na API (app Android).
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UsuarioService usuarioService;

    @GetMapping
    public UsuarioResponse eu(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return UsuarioResponse.from(principal.getUsuario());
    }

    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@AuthenticationPrincipal UsuarioAutenticado principal,
                            @Valid @RequestBody TrocarSenhaRequest req) {
        usuarioService.trocarSenha(principal.getUsername(), req);
    }
}

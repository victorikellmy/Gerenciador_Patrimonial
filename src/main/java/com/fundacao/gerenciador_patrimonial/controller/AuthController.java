package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.dto.response.UsuarioResponse;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Login/logout por sessão para a SPA React ({@code /app}). O app Android continua
 * usando HTTP Basic nas mesmas rotas {@code /api/**}.
 *
 * <pre>
 * POST /api/auth/login  {login, senha}  → 200 UsuarioResponse (cria sessão) | 401
 * POST /api/auth/logout                 → 204 (invalida sessão)
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final com.fundacao.gerenciador_patrimonial.security.TentativasLoginService tentativas;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public record LoginRequest(@NotBlank String login, @NotBlank String senha) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        // Bloqueio por tentativas vale para qualquer login (existente ou não), sem revelar qual existe.
        if (tentativas.bloqueado(req.login())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("mensagem", "Muitas tentativas de login. Aguarde alguns minutos e tente de novo."));
        }
        try {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(req.login().trim().toLowerCase(), req.senha()));
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);
            // proteção contra fixação de sessão: troca o ID se já havia sessão; senão cria uma nova
            if (request.getSession(false) != null) request.changeSessionId(); else request.getSession(true);
            securityContextRepository.saveContext(ctx, request, response);
            return ResponseEntity.ok(UsuarioResponse.from(((UsuarioAutenticado) auth.getPrincipal()).getUsuario()));
        } catch (org.springframework.security.authentication.LockedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("mensagem", "Muitas tentativas de login. Aguarde alguns minutos e tente de novo."));
        } catch (AuthenticationException e) {
            // Mesma mensagem para senha errada, login inexistente e conta inativa: não revela o estado da conta.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("mensagem", "Usuário ou senha inválidos."));
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}

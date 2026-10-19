package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import com.fundacao.gerenciador_patrimonial.dto.request.TrocarSenhaRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.LevantamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.UsuarioResponse;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import com.fundacao.gerenciador_patrimonial.service.DiariaService;
import com.fundacao.gerenciador_patrimonial.service.LevantamentoService;
import com.fundacao.gerenciador_patrimonial.service.PendenciaPatrimoniamentoService;
import com.fundacao.gerenciador_patrimonial.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Sessão do usuário autenticado na API (SPA React e app Android).
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UsuarioService usuarioService;
    private final PendenciaPatrimoniamentoService pendenciaService;
    private final LevantamentoService levantamentoService;
    private final DiariaService diariaService;
    private final com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository usuarioRepo;
    private final org.springframework.security.web.context.SecurityContextRepository contextoSessao =
            new org.springframework.security.web.context.HttpSessionSecurityContextRepository();

    /** Contadores que alimentam os badges do menu, conforme o perfil de quem está logado. */
    public record Resumo(long pendenciasPatrimoniamento, LevantamentoResponse levantamentoAberto, long diariasPendentes) {}

    @GetMapping
    public UsuarioResponse eu(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return UsuarioResponse.from(principal.getUsuario());
    }

    @GetMapping("/resumo")
    public Resumo resumo(@AuthenticationPrincipal UsuarioAutenticado principal) {
        long diarias = switch (principal.getUsuario().getPerfil()) {
            case ADMINISTRADOR -> diariaService.contar(StatusDiaria.SOLICITADO);
            case PRESIDENTE -> diariaService.contar(StatusDiaria.CONFIRMADO);
            case FINANCEIRO -> diariaService.contar(StatusDiaria.DEFERIDO);
            default -> 0L;
        };
        boolean operador = switch (principal.getUsuario().getPerfil()) {
            case ADMINISTRADOR, FISCAL -> true;
            default -> false;
        };
        return new Resumo(operador ? pendenciaService.contarPendentes() : 0L,
                operador || principal.getUsuario().getPerfil() == com.fundacao.gerenciador_patrimonial.domain.enums.Perfil.GERENTE
                        ? levantamentoService.emAndamento().orElse(null) : null,
                diarias);
    }

    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@AuthenticationPrincipal UsuarioAutenticado principal,
                            @Valid @RequestBody TrocarSenhaRequest req,
                            jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response) {
        usuarioService.trocarSenha(principal.getUsername(), req);
        // Sessão do SPA: troca o retrato pelo usuário atualizado (novo hash, sem senha provisória)
        // e rotaciona o id da sessão. As OUTRAS sessões do usuário ficam com o hash antigo e são
        // encerradas pelo SessaoUsuarioFilter na próxima requisição. Basic (app) não usa sessão.
        if (request.getSession(false) != null) {
            var atualizado = new UsuarioAutenticado(usuarioRepo.findById(principal.getId()).orElseThrow());
            var ctx = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                    .authenticated(atualizado, null, atualizado.getAuthorities()));
            org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);
            request.changeSessionId();
            contextoSessao.saveContext(ctx, request, response);
        }
    }
}

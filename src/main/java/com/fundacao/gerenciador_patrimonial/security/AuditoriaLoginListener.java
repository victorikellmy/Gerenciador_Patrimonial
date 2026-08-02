package com.fundacao.gerenciador_patrimonial.security;

import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Plugs em eventos do Spring Security para registrar login/logout na trilha
 * de auditoria — sem necessidade de filtros customizados ou wrap no UserDetailsService.
 *
 * <p>Login/falha vêm via {@link org.springframework.context.ApplicationEvent};
 * logout via {@link LogoutHandler} (registrado no SecurityConfig).</p>
 */
@Component
@RequiredArgsConstructor
public class AuditoriaLoginListener implements LogoutHandler {

    private static final String ENT = "Usuario";

    private final AuditoriaService auditoriaService;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String login = event.getAuthentication().getName();
        auditoriaService.registrar(AcaoAuditoria.LOGIN, ENT, null,
                "Login bem-sucedido: " + login);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String login = event.getAuthentication() != null
                ? String.valueOf(event.getAuthentication().getName())
                : "?";
        String motivo = event.getException() != null
                ? event.getException().getClass().getSimpleName()
                : "desconhecido";
        auditoriaService.registrar(AcaoAuditoria.LOGIN_FALHA, ENT, null,
                "Falha de login: usuário=%s, motivo=%s".formatted(login, motivo));
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String login = authentication != null ? authentication.getName() : "?";
        auditoriaService.registrar(AcaoAuditoria.LOGOUT, ENT, null, "Logout: " + login);
    }
}

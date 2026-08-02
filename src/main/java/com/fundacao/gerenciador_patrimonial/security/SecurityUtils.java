package com.fundacao.gerenciador_patrimonial.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Helpers de contexto de segurança — fonte única da regra "quem é o usuário
 * atual" (antes triplicada em AuditorAwareImpl, AuditoriaService e
 * PatrimonioService).
 */
public final class SecurityUtils {

    public static final String SYSTEM = "SYSTEM";

    private SecurityUtils() {}

    /** Login do usuário autenticado, ou {@code "SYSTEM"} se anônimo/fora de request. */
    public static String usuarioAtualOuSystem() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return SYSTEM;
        }
        return auth.getName();
    }
}

package com.fundacao.gerenciador_patrimonial.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Primeiro acesso: enquanto o usuário estiver com senha provisória (definida
 * pelo administrador), qualquer página da interface web redireciona para a
 * troca de senha. A API ({@code /api/**}) não é interceptada: o app lê
 * {@code senhaProvisoria} em {@code GET /api/me} e conduz a troca por
 * {@code POST /api/me/senha}.
 */
@Configuration
public class TrocaSenhaObrigatoriaInterceptor implements WebMvcConfigurer, HandlerInterceptor {

    public static final String ROTA_TROCA = "/minha-conta/senha";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns("/**")
                .excludePathPatterns(ROTA_TROCA, "/login", "/logout", "/error", "/api/**",
                                     "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico", "/h2-console/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u && u.getUsuario().isSenhaProvisoria()) {
            response.sendRedirect(request.getContextPath() + ROTA_TROCA + "?obrigatoria");
            return false;
        }
        return true;
    }
}

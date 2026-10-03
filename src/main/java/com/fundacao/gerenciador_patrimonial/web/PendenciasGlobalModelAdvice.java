package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.service.PendenciaPatrimoniamentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expõe em todas as views web o número de pendências de patrimoniamento em aberto
 * ({@code pendenciasPendentes}) — alimenta o sino/badge da navbar.
 */
@ControllerAdvice(basePackages = "com.fundacao.gerenciador_patrimonial.web")
@RequiredArgsConstructor
@Slf4j
public class PendenciasGlobalModelAdvice {

    private final PendenciaPatrimoniamentoService pendenciaService;

    @ModelAttribute("pendenciasPendentes")
    public long pendenciasPendentes() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return 0L;
        }
        try {
            return pendenciaService.contarPendentes();
        } catch (RuntimeException e) {
            log.debug("Não foi possível contar pendências para a navbar: {}", e.getMessage());
            return 0L;
        }
    }
}

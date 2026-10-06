package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.dto.response.LevantamentoResponse;
import com.fundacao.gerenciador_patrimonial.service.LevantamentoService;
import com.fundacao.gerenciador_patrimonial.service.PendenciaPatrimoniamentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expõe em todas as views web:
 * <ul>
 *   <li>{@code pendenciasPendentes} — pendências de patrimoniamento em aberto (sino/badge)</li>
 *   <li>{@code levantamentoAberto} — levantamento patrimonial em andamento, ou {@code null}
 *       (badge de progresso no menu e alerta do dashboard)</li>
 * </ul>
 */
@ControllerAdvice(basePackages = "com.fundacao.gerenciador_patrimonial.web")
@RequiredArgsConstructor
@Slf4j
public class PendenciasGlobalModelAdvice {

    private final PendenciaPatrimoniamentoService pendenciaService;
    private final LevantamentoService levantamentoService;

    @ModelAttribute("pendenciasPendentes")
    public long pendenciasPendentes() {
        if (!autenticado()) return 0L;
        try {
            return pendenciaService.contarPendentes();
        } catch (RuntimeException e) {
            log.debug("Não foi possível contar pendências para a navbar: {}", e.getMessage());
            return 0L;
        }
    }

    @ModelAttribute("levantamentoAberto")
    public LevantamentoResponse levantamentoAberto() {
        if (!autenticado()) return null;
        try {
            return levantamentoService.emAndamento().orElse(null);
        } catch (RuntimeException e) {
            log.debug("Não foi possível consultar o levantamento em andamento: {}", e.getMessage());
            return null;
        }
    }

    private static boolean autenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }
}

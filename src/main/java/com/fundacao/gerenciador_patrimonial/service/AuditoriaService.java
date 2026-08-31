package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.AuditoriaAcao;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.repository.AuditoriaAcaoRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Registra ações de usuário na trilha {@code auditoria_acao}.
 *
 * <p>O método {@link #registrar} usa {@link Propagation#REQUIRES_NEW} de propósito:
 * uma falha na operação principal NÃO deve apagar a trilha — e vice-versa, falhar
 * o registro de auditoria não deve abortar a operação de negócio.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaService {

    private static final String SYSTEM = "SYSTEM";

    private final AuditoriaAcaoRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(AcaoAuditoria acao, String entidade, Long entidadeId, String descricao) {
        try {
            AuditoriaAcao registro = AuditoriaAcao.builder()
                    .usuario(usuarioAtual())
                    .acao(acao)
                    .entidade(entidade)
                    .entidadeId(entidadeId)
                    .descricao(truncar(descricao, 2000))
                    .ipOrigem(ipAtual())
                    .build();
            repo.save(registro);
        } catch (Exception e) {
            // Falha de auditoria não deve quebrar a operação de negócio.
            log.error("Falha ao registrar auditoria ({} {} #{}): {}",
                    acao, entidade, entidadeId, e.getMessage());
        }
    }

    private static String usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return SYSTEM;
        }
        return auth.getName();
    }

    /** IP de origem da requisição HTTP corrente, ou {@code null} fora de contexto web. */
    private static String ipAtual() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes sra)) return null;
            HttpServletRequest req = sra.getRequest();
            // X-Forwarded-For tem prioridade quando há proxy reverso (nginx/traefik).
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int comma = xff.indexOf(',');
                return (comma > 0 ? xff.substring(0, comma) : xff).trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

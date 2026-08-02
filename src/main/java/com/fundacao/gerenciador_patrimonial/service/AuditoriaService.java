package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Registra ações de usuário na trilha {@code auditoria_acao}.
 *
 * <p>A gravação é <b>assíncrona</b> (ver {@link AuditoriaGravador}): este
 * service captura usuário e IP ainda no thread da request — onde o
 * SecurityContext e o request HTTP existem — e delega o INSERT para um
 * thread do executor, fora do caminho crítico da operação de negócio e sem
 * segurar uma segunda conexão JDBC durante a transação principal.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaService {

    private final AuditoriaGravador gravador;

    public void registrar(AcaoAuditoria acao, String entidade, Long entidadeId, String descricao) {
        // Contexto capturado AQUI: no thread assíncrono ele não existe mais.
        gravador.gravar(acao, entidade, entidadeId, descricao,
                SecurityUtils.usuarioAtualOuSystem(), ipAtual());
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
}

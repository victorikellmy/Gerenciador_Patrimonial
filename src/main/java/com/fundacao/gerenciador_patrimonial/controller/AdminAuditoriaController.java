package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.dto.response.AuditoriaResponse;
import com.fundacao.gerenciador_patrimonial.repository.AuditoriaAcaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Endpoint admin para o painel de monitoramento de logs.
 *
 * <p>Acesso restrito ao perfil ADMINISTRADOR via {@link PreAuthorize}.
 * Não há tela Thymeleaf neste turno — apenas a API consumível pelo painel.</p>
 */
@RestController
@RequestMapping("/api/admin/auditoria")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminAuditoriaController {

    private final AuditoriaAcaoRepository repo;

    /**
     * Busca paginada com filtros opcionais.
     *
     * <pre>
     * GET /api/admin/auditoria?usuario=fa&amp;acao=UPDATE&amp;entidade=Patrimonio
     *                       &amp;de=2026-04-01T00:00:00&amp;ate=2026-04-30T23:59:59
     *                       &amp;page=0&amp;size=50
     * </pre>
     */
    @GetMapping
    public Page<AuditoriaResponse> buscar(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) AcaoAuditoria acao,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) Long entidadeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        return repo.buscar(usuario, acao, entidade, entidadeId, de, ate,
                        PageRequest.of(page, size))
                .map(AuditoriaResponse::from);
    }

    /** Top-N usuários mais ativos (opcionalmente recortado por data inicial). */
    @GetMapping("/usuarios-mais-ativos")
    public List<Map<String, Object>> topUsuarios(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(defaultValue = "10") int limite) {

        return repo.contarPorUsuario(de, PageRequest.of(0, limite))
                .stream()
                .map(r -> Map.of("usuario", (Object) r[0], "total", (Object) r[1]))
                .toList();
    }
}

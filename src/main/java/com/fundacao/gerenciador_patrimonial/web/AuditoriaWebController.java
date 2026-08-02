package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.service.AuditoriaConsultaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

/**
 * Tela administrativa de visualização da trilha de auditoria.
 *
 * <p>Espelha em HTML o que {@link com.fundacao.gerenciador_patrimonial.controller.AdminAuditoriaController}
 * expõe via JSON. Acesso restrito ao perfil ADMINISTRADOR.</p>
 */
@Controller
@RequestMapping("/admin/auditoria")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AuditoriaWebController {

    private final AuditoriaConsultaService consulta;

    @GetMapping
    public String listar(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) AcaoAuditoria acao,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) Long entidadeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime ate,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size,
            Model model) {

        model.addAttribute("pagina",
                consulta.buscar(usuario, acao, entidade, entidadeId, de, ate, PageRequest.of(page, size)));
        model.addAttribute("acoes", AcaoAuditoria.values());
        model.addAttribute("topUsuarios", consulta.topUsuarios(null, 5));
        model.addAttribute("filtro", new Filtro(usuario, acao, entidade, entidadeId, de, ate));
        return "admin/auditoria";
    }

    public record Filtro(String usuario, AcaoAuditoria acao, String entidade, Long entidadeId,
                         LocalDateTime de, LocalDateTime ate) {}
}

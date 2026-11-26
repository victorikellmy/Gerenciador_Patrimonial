package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.dto.request.AcessoDominioRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.DominioStatusResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.UsuarioDominioResponse;
import com.fundacao.gerenciador_patrimonial.exception.ErroResponse;
import com.fundacao.gerenciador_patrimonial.service.dominio.DiretorioAd.DiretorioIndisponivelException;
import com.fundacao.gerenciador_patrimonial.service.dominio.DominioUsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tela "Grupos da rede": contas do AD FPTO.LOCAL e o grupo do Patrimônio de cada uma.
 * As alterações são gravadas direto no AD pela conta de serviço. Somente ADMINISTRADOR.
 *
 * <pre>
 * GET  /api/admin/dominio/status            situação da conexão e dos grupos
 * POST /api/admin/dominio/grupos            cria no AD os grupos que faltam
 * GET  /api/admin/dominio/usuarios?termo=   contas da rede (login ou nome)
 * PUT  /api/admin/dominio/usuarios/{login}  {perfil, podeAbastecer} → grava no AD
 * </pre>
 */
@RestController
@RequestMapping("/api/admin/dominio")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class DominioController {

    private final DominioUsuarioService service;

    @GetMapping("/status")
    public DominioStatusResponse status() {
        return service.status();
    }

    @PostMapping("/grupos")
    public DominioStatusResponse criarGrupos() {
        return service.criarGrupos();
    }

    @GetMapping("/usuarios")
    public List<UsuarioDominioResponse> usuarios(@RequestParam(required = false) String termo) {
        return service.listar(termo);
    }

    @PutMapping("/usuarios/{login}")
    public UsuarioDominioResponse definirAcesso(@PathVariable String login, @RequestBody AcessoDominioRequest req) {
        return service.definirAcesso(login, req.perfil(), Boolean.TRUE.equals(req.podeAbastecer()));
    }

    @ExceptionHandler(DiretorioIndisponivelException.class)
    public ResponseEntity<ErroResponse> indisponivel(DiretorioIndisponivelException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErroResponse.of(503, "AD indisponível", ex.getMessage(), req.getRequestURI()));
    }
}

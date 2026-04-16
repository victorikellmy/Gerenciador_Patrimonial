package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.dto.request.TrocarSenhaRequest;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import com.fundacao.gerenciador_patrimonial.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Rotas públicas e do próprio usuário autenticado:
 * login, logout (o handler é provido pelo Spring) e troca de senha.
 */
@Controller
@RequiredArgsConstructor
public class AuthWebController {

    private final UsuarioService usuarioService;

    // =========================================================================
    // Login
    // =========================================================================

    @GetMapping("/login")
    public String loginPage(Model model,
                            @org.springframework.web.bind.annotation.RequestParam(required = false) String erro,
                            @org.springframework.web.bind.annotation.RequestParam(required = false) String desconectado) {
        if (erro != null) model.addAttribute("erro", "Usuário ou senha inválidos.");
        if (desconectado != null) model.addAttribute("info", "Sessão encerrada com sucesso.");
        return "auth/login";
    }

    // =========================================================================
    // Troca de senha (do próprio usuário)
    // =========================================================================

    @GetMapping("/minha-conta/senha")
    public String formSenha(Model model) {
        if (!model.containsAttribute("trocarSenhaForm")) {
            model.addAttribute("trocarSenhaForm", new TrocarSenhaRequest("", "", ""));
        }
        return "auth/trocar-senha";
    }

    @PostMapping("/minha-conta/senha")
    public String trocarSenha(@Valid @ModelAttribute("trocarSenhaForm") TrocarSenhaRequest request,
                              BindingResult binding,
                              @AuthenticationPrincipal UsuarioAutenticado atual,
                              RedirectAttributes redirect,
                              Model model) {
        if (binding.hasErrors()) {
            return "auth/trocar-senha";
        }
        try {
            usuarioService.trocarSenha(atual.getUsername(), request);
            redirect.addFlashAttribute("sucesso", "Senha alterada com sucesso.");
            return "redirect:/";
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            return "auth/trocar-senha";
        }
    }
}

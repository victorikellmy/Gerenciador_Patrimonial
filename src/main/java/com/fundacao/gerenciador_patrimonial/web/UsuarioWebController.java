package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.dto.request.UsuarioRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.UsuarioResponse;
import com.fundacao.gerenciador_patrimonial.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * CRUD de usuários — restrito ao perfil ADMINISTRADOR
 * (regra aplicada no {@link com.fundacao.gerenciador_patrimonial.security.SecurityConfig}).
 */
@Controller
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioWebController {

    private final UsuarioService usuarioService;

    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size,
                         Model model) {
        Page<UsuarioResponse> pagina = usuarioService.listar(
                PageRequest.of(page, size, Sort.by("nomeCompleto")));
        model.addAttribute("pagina", pagina);
        return "usuarios/list";
    }

    @GetMapping("/novo")
    public String novoForm(Model model) {
        if (!model.containsAttribute("usuarioForm")) {
            model.addAttribute("usuarioForm",
                    new UsuarioRequest("", "", "", Perfil.FISCAL, true));
        }
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("editando", false);
        return "usuarios/form";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("usuarioForm") UsuarioRequest request,
                        BindingResult binding,
                        RedirectAttributes redirect,
                        Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("perfis", Perfil.values());
            model.addAttribute("editando", false);
            return "usuarios/form";
        }
        try {
            usuarioService.criar(request);
            redirect.addFlashAttribute("sucesso", "Usuário criado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/usuarios/novo";
        }
        return "redirect:/usuarios";
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        UsuarioResponse u = usuarioService.buscarPorId(id);
        model.addAttribute("id", id);
        // Senha vazia = "não altera"
        model.addAttribute("usuarioForm",
                new UsuarioRequest(u.nomeCompleto(), u.login(), "", u.perfil(), u.ativo()));
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("editando", true);
        return "usuarios/form";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id,
                            @Valid @ModelAttribute("usuarioForm") UsuarioRequest request,
                            BindingResult binding,
                            RedirectAttributes redirect,
                            Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("id", id);
            model.addAttribute("perfis", Perfil.values());
            model.addAttribute("editando", true);
            return "usuarios/form";
        }
        try {
            usuarioService.atualizar(id, request);
            redirect.addFlashAttribute("sucesso", "Usuário atualizado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/inativar")
    public String inativar(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            usuarioService.inativar(id);
            redirect.addFlashAttribute("sucesso", "Usuário inativado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }
}

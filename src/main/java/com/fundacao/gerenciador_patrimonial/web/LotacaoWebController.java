package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.dto.request.LotacaoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.LotacaoResponse;
import com.fundacao.gerenciador_patrimonial.service.LotacaoService;
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

@Controller
@RequestMapping("/lotacoes")
@RequiredArgsConstructor
public class LotacaoWebController {

    private final LotacaoService lotacaoService;

    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size,
                         Model model) {
        Page<LotacaoResponse> pagina = lotacaoService.listar(
                PageRequest.of(page, size, Sort.by("upm", "nome")));
        model.addAttribute("pagina", pagina);
        if (!model.containsAttribute("lotacaoForm")) {
            model.addAttribute("lotacaoForm",
                    new LotacaoRequest("", "", null, TipoLocal.INTERNO, null));
        }
        model.addAttribute("tiposLocal", TipoLocal.values());
        return "lotacoes/list";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("lotacaoForm") LotacaoRequest request,
                        BindingResult binding,
                        RedirectAttributes redirect,
                        Model model) {
        if (binding.hasErrors()) {
            return listar(0, 20, model);
        }
        try {
            lotacaoService.criar(request);
            redirect.addFlashAttribute("sucesso", "Lotação cadastrada.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/lotacoes";
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        LotacaoResponse r = lotacaoService.buscarPorId(id);
        model.addAttribute("id", id);
        model.addAttribute("lotacaoForm", new LotacaoRequest(
                r.upm(), r.nome(), r.cidade(), r.tipoLocal(), r.responsavelAtualId()));
        model.addAttribute("tiposLocal", TipoLocal.values());
        return "lotacoes/form";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id,
                            @Valid @ModelAttribute("lotacaoForm") LotacaoRequest request,
                            BindingResult binding,
                            RedirectAttributes redirect,
                            Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("id", id);
            model.addAttribute("tiposLocal", TipoLocal.values());
            return "lotacoes/form";
        }
        try {
            lotacaoService.atualizar(id, request);
            redirect.addFlashAttribute("sucesso", "Lotação atualizada.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/lotacoes";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            lotacaoService.excluir(id);
            redirect.addFlashAttribute("sucesso", "Lotação excluída.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/lotacoes";
    }
}

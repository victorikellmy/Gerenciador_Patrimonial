package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.dto.request.ResponsavelRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.LotacaoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.ResponsavelResponse;
import com.fundacao.gerenciador_patrimonial.service.LotacaoService;
import com.fundacao.gerenciador_patrimonial.service.ResponsavelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/responsaveis")
@RequiredArgsConstructor
public class ResponsavelWebController {

    private final ResponsavelService responsavelService;
    private final LotacaoService lotacaoService;

    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size,
                         Model model) {
        Page<ResponsavelResponse> pagina = responsavelService.listar(
                PageRequest.of(page, size, Sort.by("nomeCompleto")));
        model.addAttribute("pagina", pagina);
        if (!model.containsAttribute("responsavelForm")) {
            model.addAttribute("responsavelForm",
                    new ResponsavelRequest("", null, null, null));
        }
        model.addAttribute("lotacoes", carregarTodasLotacoes());
        return "responsaveis/list";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("responsavelForm") ResponsavelRequest request,
                        BindingResult binding,
                        RedirectAttributes redirect,
                        Model model) {
        if (binding.hasErrors()) {
            return listar(0, 20, model);
        }
        try {
            responsavelService.criar(request);
            redirect.addFlashAttribute("sucesso", "Responsável cadastrado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/responsaveis";
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        ResponsavelResponse r = responsavelService.buscarPorId(id);
        model.addAttribute("id", id);
        model.addAttribute("responsavelForm", new ResponsavelRequest(
                r.nomeCompleto(), r.matricula(), r.cidade(), r.lotacaoId()));
        model.addAttribute("lotacoes", carregarTodasLotacoes());
        return "responsaveis/form";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id,
                            @Valid @ModelAttribute("responsavelForm") ResponsavelRequest request,
                            BindingResult binding,
                            RedirectAttributes redirect,
                            Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("id", id);
            model.addAttribute("lotacoes", carregarTodasLotacoes());
            return "responsaveis/form";
        }
        try {
            responsavelService.atualizar(id, request);
            redirect.addFlashAttribute("sucesso", "Responsável atualizado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/responsaveis";
    }

    @PostMapping("/{id}/inativar")
    public String inativar(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            responsavelService.inativar(id);
            redirect.addFlashAttribute("sucesso", "Responsável inativado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/responsaveis";
    }

    /** Carrega todas as lotações (paginado apenas para não estourar memória em cenários grandes). */
    private java.util.List<LotacaoResponse> carregarTodasLotacoes() {
        Pageable pageable = PageRequest.of(0, 500, Sort.by("upm", "nome"));
        return lotacaoService.listar(pageable).getContent();
    }
}

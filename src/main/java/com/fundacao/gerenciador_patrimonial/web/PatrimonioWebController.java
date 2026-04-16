package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoAnexo;
import com.fundacao.gerenciador_patrimonial.dto.request.BaixaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroPatrimonio;
import com.fundacao.gerenciador_patrimonial.dto.request.MovimentacaoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.LotacaoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.PatrimonioResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.ResponsavelResponse;
import com.fundacao.gerenciador_patrimonial.service.AnexoService;
import com.fundacao.gerenciador_patrimonial.service.LotacaoService;
import com.fundacao.gerenciador_patrimonial.service.PatrimonioService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/patrimonios")
@RequiredArgsConstructor
public class PatrimonioWebController {

    private final PatrimonioService patrimonioService;
    private final LotacaoService lotacaoService;
    private final ResponsavelService responsavelService;
    private final AnexoService anexoService;

    // =========================================================================
    // Listagem com filtros
    // =========================================================================

    @GetMapping
    public String listar(@ModelAttribute FiltroPatrimonio filtro,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size,
                         Model model) {
        Page<PatrimonioResponse> pagina = patrimonioService.pesquisar(
                filtro, PageRequest.of(page, size, Sort.by("id").descending()));

        model.addAttribute("pagina", pagina);
        model.addAttribute("filtro", filtro);
        model.addAttribute("situacoes", SituacaoPatrimonio.values());
        model.addAttribute("conservacoes", Conservacao.values());
        return "patrimonios/list";
    }

    // =========================================================================
    // Criar / editar
    // =========================================================================

    @GetMapping("/novo")
    public String novoForm(Model model) {
        if (!model.containsAttribute("patrimonioForm")) {
            model.addAttribute("patrimonioForm",
                    new PatrimonioRequest(null, "", null, null, null, null, null, null, null));
        }
        prepararListasAuxiliares(model);
        model.addAttribute("editando", false);
        model.addAttribute("id", null);
        return "patrimonios/form";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("patrimonioForm") PatrimonioRequest request,
                        BindingResult binding,
                        RedirectAttributes redirect,
                        Model model) {
        if (binding.hasErrors()) {
            prepararListasAuxiliares(model);
            model.addAttribute("editando", false);
            return "patrimonios/form";
        }
        try {
            PatrimonioResponse criado = patrimonioService.criar(request);
            redirect.addFlashAttribute("sucesso", "Patrimônio criado (ID " + criado.id() + ").");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/patrimonios";
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        PatrimonioResponse p = patrimonioService.buscarPorId(id);
        model.addAttribute("patrimonioForm", new PatrimonioRequest(
                p.numeroTombo(), p.descricao(), p.categoria(), p.dataCompra(),
                p.valorCompra(), p.conservacao(), p.notaFiscal(),
                p.lotacaoId(), p.responsavelId()
        ));
        model.addAttribute("id", id);
        model.addAttribute("editando", true);
        model.addAttribute("patrimonio", p);
        model.addAttribute("anexos", anexoService.listar(id));
        model.addAttribute("tiposAnexo", TipoAnexo.values());
        prepararListasAuxiliares(model);
        return "patrimonios/form";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id,
                            @Valid @ModelAttribute("patrimonioForm") PatrimonioRequest request,
                            BindingResult binding,
                            RedirectAttributes redirect,
                            Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("id", id);
            model.addAttribute("editando", true);
            model.addAttribute("patrimonio", patrimonioService.buscarPorId(id));
            prepararListasAuxiliares(model);
            return "patrimonios/form";
        }
        try {
            patrimonioService.atualizar(id, request);
            redirect.addFlashAttribute("sucesso", "Patrimônio atualizado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/patrimonios/" + id + "/editar";
    }

    // =========================================================================
    // Movimentação
    // =========================================================================

    @GetMapping("/{id}/movimentar")
    public String movimentarForm(@PathVariable Long id, Model model) {
        PatrimonioResponse p = patrimonioService.buscarPorId(id);
        model.addAttribute("patrimonio", p);
        model.addAttribute("movimentacaoForm", new MovimentacaoRequest(null, null, null));
        prepararListasAuxiliares(model);
        return "patrimonios/movimentar";
    }

    @PostMapping("/{id}/movimentar")
    public String movimentar(@PathVariable Long id,
                             @Valid @ModelAttribute("movimentacaoForm") MovimentacaoRequest request,
                             RedirectAttributes redirect) {
        try {
            patrimonioService.movimentar(id, request);
            redirect.addFlashAttribute("sucesso", "Patrimônio movimentado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/patrimonios/" + id + "/movimentar";
        }
        return "redirect:/patrimonios";
    }

    // =========================================================================
    // Baixa
    // =========================================================================

    @GetMapping("/{id}/baixa")
    public String baixaForm(@PathVariable Long id, Model model) {
        model.addAttribute("patrimonio", patrimonioService.buscarPorId(id));
        model.addAttribute("baixaForm", new BaixaRequest(""));
        return "patrimonios/baixa";
    }

    @PostMapping("/{id}/baixa")
    public String darBaixa(@PathVariable Long id,
                           @Valid @ModelAttribute("baixaForm") BaixaRequest request,
                           BindingResult binding,
                           RedirectAttributes redirect,
                           Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("patrimonio", patrimonioService.buscarPorId(id));
            return "patrimonios/baixa";
        }
        try {
            patrimonioService.darBaixa(id, request);
            redirect.addFlashAttribute("sucesso", "Patrimônio baixado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/patrimonios";
    }

    // =========================================================================
    // Exclusão definitiva
    // =========================================================================

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            patrimonioService.excluirPermanentemente(id);
            redirect.addFlashAttribute("sucesso", "Patrimônio excluído definitivamente.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/patrimonios";
    }

    // =========================================================================
    // Anexos (upload e exclusão)
    // =========================================================================

    @PostMapping("/{id}/anexos")
    public String uploadAnexo(@PathVariable Long id,
                              @RequestParam("arquivo") MultipartFile arquivo,
                              @RequestParam(value = "tipo", required = false) TipoAnexo tipo,
                              RedirectAttributes redirect) {
        try {
            anexoService.anexar(id, arquivo, tipo);
            redirect.addFlashAttribute("sucesso", "Anexo enviado.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/patrimonios/" + id + "/editar";
    }

    @PostMapping("/anexos/{anexoId}/excluir")
    public String excluirAnexo(@PathVariable Long anexoId,
                               @RequestParam Long patrimonioId,
                               RedirectAttributes redirect) {
        try {
            anexoService.excluir(anexoId);
            redirect.addFlashAttribute("sucesso", "Anexo removido.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/patrimonios/" + patrimonioId + "/editar";
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private void prepararListasAuxiliares(Model model) {
        Pageable todos = PageRequest.of(0, 1000, Sort.by("upm", "nome"));
        List<LotacaoResponse> lotacoes = lotacaoService.listar(todos).getContent();

        Pageable respPg = PageRequest.of(0, 1000, Sort.by("nomeCompleto"));
        List<ResponsavelResponse> responsaveis = responsavelService.listar(respPg).getContent();

        model.addAttribute("lotacoes", lotacoes);
        model.addAttribute("responsaveis", responsaveis);
        model.addAttribute("conservacoes", Conservacao.values());
    }
}

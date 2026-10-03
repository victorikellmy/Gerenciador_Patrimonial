package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.domain.catalog.SubcategoriaCatalog;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusPendencia;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoAnexo;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.PatrimonioResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.PendenciaResponse;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.service.AnexoService;
import com.fundacao.gerenciador_patrimonial.service.LotacaoService;
import com.fundacao.gerenciador_patrimonial.service.PendenciaPatrimoniamentoService;
import com.fundacao.gerenciador_patrimonial.service.ResponsavelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;

/**
 * Telas de pendências de patrimoniamento (recebidas do Almoxarifado).
 *
 * <ul>
 *   <li>{@code GET  /pendencias} — lista (PENDENTE por padrão)</li>
 *   <li>{@code GET  /pendencias/{id}} — detalhes + itens + NF</li>
 *   <li>{@code GET  /pendencias/{id}/patrimoniar} — formulário de Novo patrimônio pré-preenchido</li>
 *   <li>{@code POST /pendencias/{id}/patrimoniar} — cria o bem, copia a NF, avança a pendência</li>
 *   <li>{@code POST /pendencias/{id}/concluir} — conclui manualmente</li>
 *   <li>{@code POST /pendencias/{id}/descartar} — descarta (ADMINISTRADOR)</li>
 * </ul>
 */
@Controller
@RequestMapping("/pendencias")
@RequiredArgsConstructor
public class PendenciaWebController {

    private final PendenciaPatrimoniamentoService pendenciaService;
    private final LotacaoService lotacaoService;
    private final ResponsavelService responsavelService;
    private final AnexoService anexoService;
    private final PatrimonioRepository patrimonioRepository;
    private final SubcategoriaCatalog subcategoriaCatalog;

    @GetMapping
    public String listar(@RequestParam(required = false) StatusPendencia status,
                         @RequestParam(required = false, defaultValue = "false") boolean todas,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size,
                         Model model) {
        StatusPendencia filtro = todas ? null : (status != null ? status : StatusPendencia.PENDENTE);
        Page<PendenciaResponse> pagina = pendenciaService.listar(
                filtro, PageRequest.of(page, size, Sort.by("criadoEm").descending()));

        model.addAttribute("pagina", pagina);
        model.addAttribute("status", filtro);
        model.addAttribute("todas", todas);
        model.addAttribute("statusOpcoes", StatusPendencia.values());
        model.addAttribute("totalPendentes", pendenciaService.contarPendentes());
        return "pendencias/list";
    }

    @GetMapping("/{id}")
    public String detalhe(@PathVariable Long id, Model model) {
        model.addAttribute("pendencia", pendenciaService.buscar(id));
        return "pendencias/detalhe";
    }

    @GetMapping("/{id}/nota-fiscal")
    public ResponseEntity<Resource> notaFiscal(@PathVariable Long id) {
        var nf = pendenciaService.notaFiscal(id);
        String nome = nf.nomeOriginal() != null ? nf.nomeOriginal() : "nota-fiscal.pdf";
        String nomeAscii = nome.replaceAll("[^\\x20-\\x7E]", "_");
        String nomeUtf8 = java.net.URLEncoder.encode(nome, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType tipo = MediaType.APPLICATION_OCTET_STREAM;
        try { if (nf.contentType() != null) tipo = MediaType.parseMediaType(nf.contentType()); }
        catch (RuntimeException ignored) { /* mantém octet-stream */ }

        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + nomeAscii + "\"; filename*=UTF-8''" + nomeUtf8)
                .body(nf.recurso());
    }

    // =========================================================================
    // Patrimoniar — reutiliza patrimonios/form.html
    // =========================================================================

    @GetMapping("/{id}/patrimoniar")
    public String patrimoniarForm(@PathVariable Long id, Model model) {
        PendenciaResponse pendencia = pendenciaService.buscar(id);
        if (!model.containsAttribute("patrimonioForm")) {
            model.addAttribute("patrimonioForm", pendenciaService.prepararCadastro(id));
        }
        prepararFormulario(model, pendencia);
        return "patrimonios/form";
    }

    @PostMapping("/{id}/patrimoniar")
    public String patrimoniar(@PathVariable Long id,
                              @Valid @ModelAttribute("patrimonioForm") PatrimonioRequest request,
                              BindingResult binding,
                              @RequestParam(value = "anexos", required = false) MultipartFile[] anexos,
                              @RequestParam(value = "tipoAnexo", required = false) TipoAnexo tipoAnexo,
                              RedirectAttributes redirect,
                              Model model) {
        if (binding.hasErrors()) {
            prepararFormulario(model, pendenciaService.buscar(id));
            return "patrimonios/form";
        }
        try {
            PatrimonioResponse criado = pendenciaService.patrimoniar(id, request);
            anexarExtras(criado.id(), anexos, tipoAnexo);

            PendenciaResponse depois = pendenciaService.buscar(id);
            if (depois.pendente()) {
                redirect.addFlashAttribute("sucesso",
                        "Patrimônio criado (ID " + criado.id() + "). Faltam "
                        + depois.quantidadeRestante() + " unidade(s) desta pendência.");
                return "redirect:/pendencias/" + id + "/patrimoniar";
            }
            redirect.addFlashAttribute("sucesso",
                    "Patrimônio criado (ID " + criado.id() + "). Pendência #" + id + " concluída.");
            return "redirect:/pendencias";
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pendencias/" + id;
        }
    }

    @PostMapping("/{id}/concluir")
    public String concluir(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            pendenciaService.concluirManualmente(id);
            redirect.addFlashAttribute("sucesso", "Pendência #" + id + " concluída.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pendencias";
    }

    @PostMapping("/{id}/descartar")
    public String descartar(@PathVariable Long id,
                            @RequestParam(required = false) String motivo,
                            RedirectAttributes redirect) {
        try {
            pendenciaService.descartar(id, motivo);
            redirect.addFlashAttribute("sucesso", "Pendência #" + id + " descartada.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pendencias";
    }

    // ------------------------------------------------------------------ helpers

    private void prepararFormulario(Model model, PendenciaResponse pendencia) {
        model.addAttribute("pendencia",     pendencia);
        model.addAttribute("editando",      false);
        model.addAttribute("id",            null);
        model.addAttribute("lotacoes",      lotacaoService.listarParaSelect());
        model.addAttribute("responsaveis",  responsavelService.listarParaSelect());
        model.addAttribute("conservacoes",  Conservacao.values());
        model.addAttribute("categorias",    patrimonioRepository.findDistinctCategorias());
        model.addAttribute("subcategorias", subcategoriaCatalog.disponiveis());
        model.addAttribute("tiposAnexo",    TipoAnexo.values());
    }

    /** Anexos adicionais enviados no formulário (a NF da pendência é copiada pelo serviço). */
    private void anexarExtras(Long patrimonioId, MultipartFile[] arquivos, TipoAnexo tipo) {
        if (arquivos == null) return;
        for (MultipartFile f : arquivos) {
            if (f == null || f.isEmpty()) continue;
            try {
                anexoService.anexar(patrimonioId, f, tipo);
            } catch (RuntimeException ignored) {
                // não trava o fluxo; usuário pode reenviar pela tela de edição
            }
        }
    }
}

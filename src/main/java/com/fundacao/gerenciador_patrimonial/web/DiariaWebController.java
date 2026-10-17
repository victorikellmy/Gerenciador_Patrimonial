package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.domain.catalog.FrotaCatalog;
import com.fundacao.gerenciador_patrimonial.domain.enums.MeioTransporte;
import com.fundacao.gerenciador_patrimonial.domain.enums.RecursoDiaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import com.fundacao.gerenciador_patrimonial.dto.request.DiariaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroFrota;
import com.fundacao.gerenciador_patrimonial.dto.request.NivelDiariaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.ProfissionalRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.DiariaResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.ProfissionalResponse;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import com.fundacao.gerenciador_patrimonial.service.DiariaService;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.DiariaPdfExporter;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.FrotaExporter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Diárias de viagem (fluxo do antigo Transportes FPTO).
 *
 * <ul>
 *   <li>{@code /diarias} — lista por situação (presidente vê confirmadas, financeiro vê deferidas)</li>
 *   <li>{@code /diarias/nova}, {@code /diarias/{id}/editar} — ADMIN/FISCAL</li>
 *   <li>{@code /diarias/{id}} — detalhe com ações por perfil e histórico; {@code /pdf} — formulário para assinatura</li>
 *   <li>{@code /diarias/{id}/confirmar} (ADMIN), {@code /deferir}, {@code /indeferir} (PRESIDENTE), {@code /pagar} (FINANCEIRO)</li>
 *   <li>{@code /diarias/pagas} — relatório por data de pagamento (XLSX/PDF)</li>
 *   <li>{@code /diarias/profissionais}, {@code /diarias/niveis} — cadastros (ADMIN/FISCAL)</li>
 * </ul>
 */
@Controller
@RequestMapping("/diarias")
@RequiredArgsConstructor
public class DiariaWebController {

    private final DiariaService service;
    private final DiariaPdfExporter pdf;
    private final FrotaExporter exporter;

    // =========================================================================
    // Listagem / detalhe
    // =========================================================================

    @GetMapping
    public String listar(@RequestParam(required = false) String status,
                         @ModelAttribute("filtro") FiltroFrota filtro,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "25") int size,
                         @AuthenticationPrincipal UsuarioAutenticado logado, Model model) {
        StatusDiaria st = "TODAS".equalsIgnoreCase(status) ? null
                : status != null && !status.isBlank() ? StatusDiaria.valueOf(status) : DiariaService.statusPadrao(logado);
        Page<DiariaResponse> pagina = service.listar(st, filtro,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                        Sort.by(Sort.Order.desc("dataSolicitacao"), Sort.Order.desc("id"))));
        model.addAttribute("pagina", pagina);
        model.addAttribute("status", st);
        model.addAttribute("statusSelecionado", st != null ? st.name() : "TODAS");
        model.addAttribute("statusOpcoes", StatusDiaria.values());
        model.addAttribute("contagens", Map.of(
                "SOLICITADO", service.contar(StatusDiaria.SOLICITADO),
                "CONFIRMADO", service.contar(StatusDiaria.CONFIRMADO),
                "DEFERIDO", service.contar(StatusDiaria.DEFERIDO)));
        return "diarias/list";
    }

    @GetMapping("/{id:\\d+}")
    public String detalhe(@PathVariable Long id, Model model) {
        model.addAttribute("diaria", service.buscar(id));
        model.addAttribute("historico", service.historico(id));
        model.addAttribute("hoje", LocalDate.now());
        return "diarias/detalhe";
    }

    @GetMapping("/{id:\\d+}/pdf")
    public ResponseEntity<byte[]> imprimir(@PathVariable Long id) {
        DiariaResponse d = service.buscar(id);
        byte[] bytes = pdf.solicitacaoDiaria(d, service.historico(id));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"diaria-" + id + ".pdf\"").body(bytes);
    }

    // =========================================================================
    // Criar / editar
    // =========================================================================

    @GetMapping("/nova")
    public String novaForm(Model model) {
        if (!model.containsAttribute("diariaForm")) {
            model.addAttribute("diariaForm", new DiariaRequest(null, "", LocalDate.now(), null, LocalDate.now(), null,
                    MeioTransporte.VEICULO_OFICIAL, RecursoDiaria.PROPRIO, null, null, null));
        }
        prepararForm(model, null);
        return "diarias/form";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("diariaForm") DiariaRequest req, BindingResult binding,
                        RedirectAttributes redirect, Model model) {
        if (binding.hasErrors()) {
            prepararForm(model, null);
            return "diarias/form";
        }
        try {
            DiariaResponse d = service.criar(req);
            redirect.addFlashAttribute("sucesso", "Solicitação de diária nº " + d.id() + " aberta.");
            return "redirect:/diarias/" + d.id();
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            prepararForm(model, null);
            return "diarias/form";
        }
    }

    @GetMapping("/{id:\\d+}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        DiariaResponse d = service.buscar(id);
        if (!model.containsAttribute("diariaForm")) {
            model.addAttribute("diariaForm", new DiariaRequest(d.profissionalId(), d.itinerario(), d.partida(), d.horaSaida(),
                    d.retorno(), d.horaChegada(), d.meioTransporte(), d.recurso(), d.numeroDiarias(), d.valorUnitario(), d.finalidade()));
        }
        prepararForm(model, d);
        return "diarias/form";
    }

    @PostMapping("/{id:\\d+}")
    public String atualizar(@PathVariable Long id, @Valid @ModelAttribute("diariaForm") DiariaRequest req,
                            BindingResult binding, RedirectAttributes redirect, Model model) {
        DiariaResponse atual = service.buscar(id);
        if (binding.hasErrors()) {
            prepararForm(model, atual);
            return "diarias/form";
        }
        try {
            service.atualizar(id, req);
            redirect.addFlashAttribute("sucesso", "Solicitação atualizada.");
            return "redirect:/diarias/" + id;
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            prepararForm(model, atual);
            return "diarias/form";
        }
    }

    @PostMapping("/{id:\\d+}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            service.excluir(id);
            redirect.addFlashAttribute("sucesso", "Solicitação nº " + id + " excluída.");
            return "redirect:/diarias";
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/diarias/" + id;
        }
    }

    // =========================================================================
    // Transições do fluxo
    // =========================================================================

    @PostMapping("/{id:\\d+}/confirmar")
    public String confirmar(@PathVariable Long id, @RequestParam(required = false) String observacao, RedirectAttributes redirect) {
        return transicionar(id, StatusDiaria.CONFIRMADO, observacao, null, redirect);
    }

    @PostMapping("/{id:\\d+}/deferir")
    public String deferir(@PathVariable Long id, @RequestParam(required = false) String observacao, RedirectAttributes redirect) {
        return transicionar(id, StatusDiaria.DEFERIDO, observacao, null, redirect);
    }

    @PostMapping("/{id:\\d+}/indeferir")
    public String indeferir(@PathVariable Long id, @RequestParam(required = false) String observacao, RedirectAttributes redirect) {
        return transicionar(id, StatusDiaria.INDEFERIDO, observacao, null, redirect);
    }

    @PostMapping("/{id:\\d+}/pagar")
    public String pagar(@PathVariable Long id,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataPagamento,
                        @RequestParam(required = false) String observacao, RedirectAttributes redirect) {
        return transicionar(id, StatusDiaria.PAGO, observacao, dataPagamento, redirect);
    }

    private String transicionar(Long id, StatusDiaria destino, String obs, LocalDate dataPagamento, RedirectAttributes redirect) {
        try {
            DiariaResponse d = service.transicionar(id, destino, obs, dataPagamento);
            redirect.addFlashAttribute("sucesso", "Diária nº " + id + " agora está " + d.status().getRotulo().toLowerCase() + ".");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/diarias/" + id;
    }

    /** Valores da tabela do profissional, para o formulário sugerir o valor unitário. */
    @GetMapping("/valores/{profissionalId}")
    @ResponseBody
    public Map<String, Object> valores(@PathVariable Long profissionalId) {
        ProfissionalResponse p = service.valoresDoProfissional(profissionalId);
        return Map.of("nivel", p.nivelDescricao() != null ? p.nivelDescricao() : "",
                "capitais", p.valorCapitais(), "interiorEstado", p.valorInteriorEstado(),
                "interiorOutroEstado", p.valorInteriorOutroEstado());
    }

    // =========================================================================
    // Relatório de diárias pagas
    // =========================================================================

    @GetMapping("/pagas")
    public String pagas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                        Model model) {
        LocalDate[] p = periodo(de, ate);
        List<DiariaResponse> lista = service.pagasNoPeriodo(p[0], p[1]);
        model.addAttribute("lista", lista);
        model.addAttribute("de", p[0]);
        model.addAttribute("ate", p[1]);
        model.addAttribute("total", service.totalPagoNoPeriodo(p[0], p[1]));
        return "diarias/pagas";
    }

    @GetMapping("/pagas/download")
    public void baixarPagas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                            @RequestParam(defaultValue = "pdf") String formato,
                            @AuthenticationPrincipal UsuarioAutenticado logado,
                            HttpServletResponse response) throws IOException {
        LocalDate[] p = periodo(de, ate);
        List<DiariaResponse> lista = service.pagasNoPeriodo(p[0], p[1]);
        String nome = "diarias-pagas-" + p[0] + "-a-" + p[1];
        if ("xlsx".equalsIgnoreCase(formato)) {
            List<List<Object>> linhas = new ArrayList<>();
            for (DiariaResponse d : lista) {
                linhas.add(Arrays.asList(d.id(), d.profissionalNome(), d.profissionalLotacao(), d.itinerario(), d.partida(),
                        d.retorno(), d.numeroDiarias(), d.valorUnitario(), d.valorTotal(), d.dataPagamento(), d.recurso().getRotulo()));
            }
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + nome + ".xlsx\"");
            exporter.xlsx(new FrotaExporter.Tabela("Diárias pagas", "Por data de pagamento: " + p[0] + " a " + p[1],
                    List.of("Nº", "Profissional", "Lotação", "Itinerário", "Partida", "Retorno", "Diárias", "Valor unit. (R$)",
                            "Valor pago (R$)", "Dt. pagamento", "Recurso"), linhas), response.getOutputStream());
        } else {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + nome + ".pdf\"");
            response.getOutputStream().write(pdf.diariasPagas(lista, p[0], p[1], logado != null ? logado.getNomeCompleto() : ""));
        }
        response.flushBuffer();
    }

    // =========================================================================
    // Profissionais
    // =========================================================================

    @GetMapping("/profissionais")
    public String profissionais(@RequestParam(required = false) String busca, Model model) {
        model.addAttribute("profissionais", service.listarProfissionais(busca));
        model.addAttribute("busca", busca);
        return "diarias/profissionais";
    }

    @GetMapping({"/profissionais/novo", "/profissionais/{id:\\d+}/editar"})
    public String profissionalForm(@PathVariable(required = false) Long id, Model model) {
        ProfissionalResponse p = id != null ? service.buscarProfissional(id) : null;
        if (!model.containsAttribute("profissionalForm")) {
            model.addAttribute("profissionalForm", p == null
                    ? new ProfissionalRequest("", null, null, null, "", null, null, null)
                    : new ProfissionalRequest(p.nome(), p.matricula(), p.lotacao(), p.nivelId(), p.cpf(), p.rg(), p.orgaoExpedidor(), p.dadosBancarios()));
        }
        model.addAttribute("profissional", p);
        model.addAttribute("niveis", service.niveisAtivos());
        model.addAttribute("lotacoesFrota", FrotaCatalog.LOTACOES);
        return "diarias/profissional-form";
    }

    @PostMapping({"/profissionais", "/profissionais/{id:\\d+}"})
    public String salvarProfissional(@PathVariable(required = false) Long id,
                                     @Valid @ModelAttribute("profissionalForm") ProfissionalRequest req, BindingResult binding,
                                     RedirectAttributes redirect, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("profissional", id != null ? service.buscarProfissional(id) : null);
            model.addAttribute("niveis", service.niveisAtivos());
            model.addAttribute("lotacoesFrota", FrotaCatalog.LOTACOES);
            return "diarias/profissional-form";
        }
        try {
            ProfissionalResponse p = service.salvarProfissional(id, req);
            redirect.addFlashAttribute("sucesso", "Profissional " + p.nome() + " salvo.");
            return "redirect:/diarias/profissionais";
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("profissional", id != null ? service.buscarProfissional(id) : null);
            model.addAttribute("niveis", service.niveisAtivos());
            model.addAttribute("lotacoesFrota", FrotaCatalog.LOTACOES);
            return "diarias/profissional-form";
        }
    }

    @PostMapping("/profissionais/{id:\\d+}/ativo")
    public String ativoProfissional(@PathVariable Long id, @RequestParam boolean ativo, RedirectAttributes redirect) {
        service.alterarAtivoProfissional(id, ativo);
        redirect.addFlashAttribute("sucesso", ativo ? "Profissional reativado." : "Profissional inativado.");
        return "redirect:/diarias/profissionais";
    }

    // =========================================================================
    // Tabela de diárias (níveis)
    // =========================================================================

    @GetMapping("/niveis")
    public String niveis(@RequestParam(required = false) Long editar, Model model) {
        model.addAttribute("niveis", service.listarNiveis());
        if (!model.containsAttribute("nivelForm")) {
            var n = editar != null ? service.listarNiveis().stream().filter(x -> x.id().equals(editar)).findFirst().orElse(null) : null;
            model.addAttribute("nivelForm", n == null ? new NivelDiariaRequest("", null, null, null)
                    : new NivelDiariaRequest(n.descricao(), n.valorCapitais(), n.valorInteriorEstado(), n.valorInteriorOutroEstado()));
            model.addAttribute("editandoId", n != null ? n.id() : null);
        }
        return "diarias/niveis";
    }

    @PostMapping({"/niveis", "/niveis/{id:\\d+}"})
    public String salvarNivel(@PathVariable(required = false) Long id,
                              @Valid @ModelAttribute("nivelForm") NivelDiariaRequest req, BindingResult binding,
                              RedirectAttributes redirect, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("niveis", service.listarNiveis());
            model.addAttribute("editandoId", id);
            return "diarias/niveis";
        }
        service.salvarNivel(id, req);
        redirect.addFlashAttribute("sucesso", "Tabela de diárias atualizada.");
        return "redirect:/diarias/niveis";
    }

    @PostMapping("/niveis/{id:\\d+}/ativo")
    public String ativoNivel(@PathVariable Long id, @RequestParam boolean ativo, RedirectAttributes redirect) {
        service.alterarAtivoNivel(id, ativo);
        redirect.addFlashAttribute("sucesso", ativo ? "Nível reativado." : "Nível inativado.");
        return "redirect:/diarias/niveis";
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private void prepararForm(Model model, DiariaResponse existente) {
        model.addAttribute("diaria", existente);
        model.addAttribute("editando", existente != null);
        model.addAttribute("profissionais", service.profissionaisAtivos());
        model.addAttribute("meios", MeioTransporte.values());
        model.addAttribute("recursos", RecursoDiaria.values());
    }

    private static LocalDate[] periodo(LocalDate de, LocalDate ate) {
        LocalDate hoje = LocalDate.now();
        LocalDate d = de != null ? de : hoje.withDayOfMonth(1);
        LocalDate a = ate != null ? ate : hoje;
        return new LocalDate[]{d, a};
    }
}

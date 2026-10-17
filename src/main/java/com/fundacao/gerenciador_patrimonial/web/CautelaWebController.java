package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.domain.catalog.FrotaCatalog;
import com.fundacao.gerenciador_patrimonial.dto.request.CautelaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroFrota;
import com.fundacao.gerenciador_patrimonial.dto.request.FinalizarDeslocamentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.CautelaResponse;
import com.fundacao.gerenciador_patrimonial.repository.TitularRepository;
import com.fundacao.gerenciador_patrimonial.service.CautelaService;
import com.fundacao.gerenciador_patrimonial.service.FrotaService;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.DiariaPdfExporter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Cautelas de veículo (entrega a condutor externo com termo de responsabilidade)
 * e autocomplete de titulares para os formulários da frota.
 */
@Controller
@RequestMapping("/frota")
@RequiredArgsConstructor
public class CautelaWebController {

    private final CautelaService service;
    private final FrotaService frota;
    private final DiariaPdfExporter pdf;
    private final TitularRepository titularRepo;

    @GetMapping("/cautelas")
    public String listar(@ModelAttribute("filtro") FiltroFrota filtro,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "25") int size, Model model) {
        Page<CautelaResponse> pagina = service.listar(filtro, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 200), Sort.by(Sort.Order.desc("dataViagem"), Sort.Order.desc("id"))));
        model.addAttribute("pagina", pagina);
        model.addAttribute("veiculos", frota.veiculosParaSelect());
        model.addAttribute("finalizarForm", new FinalizarDeslocamentoRequest(null, null));
        return "frota/cautelas";
    }

    @GetMapping({"/cautelas/nova", "/{id:\\d+}/cautelas/nova"})
    public String novaForm(@PathVariable(required = false) Long id, Model model) {
        if (!model.containsAttribute("cautelaForm")) {
            var v = id != null ? frota.buscarVeiculo(id) : null;
            model.addAttribute("cautelaForm", new CautelaRequest(id, LocalDate.now(), "", "Cautela", null, null,
                    LocalTime.now().withSecond(0).withNano(0), v != null ? v.kmAtual() : null, null, null,
                    "", "", "", "", "", null));
        }
        preparar(model, null);
        return "frota/cautela-form";
    }

    @PostMapping("/cautelas")
    public String criar(@Valid @ModelAttribute("cautelaForm") CautelaRequest req, BindingResult binding,
                        RedirectAttributes redirect, Model model) {
        if (req.veiculoId() == null) binding.rejectValue("veiculoId", "obrigatorio", "Selecione o veículo");
        if (binding.hasErrors()) {
            preparar(model, null);
            return "frota/cautela-form";
        }
        try {
            CautelaResponse c = service.criar(req);
            redirect.addFlashAttribute("sucesso", "Cautela nº " + c.id() + " registrada para " + c.placa()
                    + ". Imprima o termo e recolha a assinatura do condutor.");
            return "redirect:/frota/cautelas";
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            preparar(model, null);
            return "frota/cautela-form";
        }
    }

    @GetMapping("/cautelas/{id:\\d+}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        CautelaResponse c = service.buscar(id);
        if (!model.containsAttribute("cautelaForm")) {
            model.addAttribute("cautelaForm", new CautelaRequest(c.veiculoId(), c.dataViagem(), c.destino(), c.motivo(),
                    c.responsavel(), c.assistido(), c.horaSaida(), c.kmInicial(), c.horaChegada(), c.kmFinal(),
                    c.motoristaNome(), c.rg(), c.cpf(), c.fone(), c.lotacao(), c.observacao()));
        }
        preparar(model, c);
        return "frota/cautela-form";
    }

    @PostMapping("/cautelas/{id:\\d+}")
    public String atualizar(@PathVariable Long id, @Valid @ModelAttribute("cautelaForm") CautelaRequest req,
                            BindingResult binding, RedirectAttributes redirect, Model model) {
        if (binding.hasErrors()) {
            preparar(model, service.buscar(id));
            return "frota/cautela-form";
        }
        try {
            service.atualizar(id, req);
            redirect.addFlashAttribute("sucesso", "Cautela atualizada.");
            return "redirect:/frota/cautelas";
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            preparar(model, service.buscar(id));
            return "frota/cautela-form";
        }
    }

    @PostMapping("/cautelas/{id:\\d+}/finalizar")
    public String finalizar(@PathVariable Long id, @Valid @ModelAttribute("finalizarForm") FinalizarDeslocamentoRequest req,
                            BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("erro", "Informe o km final para encerrar a cautela.");
            return "redirect:/frota/cautelas";
        }
        try {
            CautelaResponse c = service.finalizar(id, req);
            redirect.addFlashAttribute("sucesso", "Cautela nº " + id + " encerrada: " + c.kmRodados() + " km rodados.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/frota/cautelas";
    }

    @PostMapping("/cautelas/{id:\\d+}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        service.excluir(id);
        redirect.addFlashAttribute("sucesso", "Cautela excluída.");
        return "redirect:/frota/cautelas";
    }

    /** Termo de cautela em PDF, para assinatura do condutor. */
    @GetMapping("/cautelas/{id:\\d+}/termo")
    public ResponseEntity<byte[]> termo(@PathVariable Long id) {
        byte[] bytes = pdf.termoCautela(service.buscar(id));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"cautela-" + id + ".pdf\"").body(bytes);
    }

    /** Autocomplete do campo Titular (base de militares/pensionistas), para a sessão web. */
    @GetMapping("/titulares")
    @ResponseBody
    public List<String> titulares(@RequestParam(defaultValue = "") String termo) {
        String t = termo.trim().toLowerCase();
        if (t.length() < 2) return List.of();
        return titularRepo.sugerirNomes("%" + t + "%", PageRequest.of(0, 8));
    }

    private void preparar(Model model, CautelaResponse existente) {
        model.addAttribute("cautela", existente);
        model.addAttribute("editando", existente != null);
        model.addAttribute("veiculos", frota.veiculosParaSelect());
        model.addAttribute("motivos", FrotaCatalog.MOTIVOS_CAUTELA);
        model.addAttribute("lotacoesFrota", FrotaCatalog.LOTACOES);
    }
}

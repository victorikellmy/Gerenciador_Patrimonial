package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.domain.catalog.FrotaCatalog;
import com.fundacao.gerenciador_patrimonial.domain.enums.Combustivel;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoServicoVeiculo;
import com.fundacao.gerenciador_patrimonial.dto.request.AbastecimentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.DeslocamentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroFrota;
import com.fundacao.gerenciador_patrimonial.dto.request.FinalizarDeslocamentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.ManutencaoVeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.VeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.AbastecimentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.DeslocamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.HistoricoVeiculoItem;
import com.fundacao.gerenciador_patrimonial.dto.response.ManutencaoVeiculoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.VeiculoResponse;
import com.fundacao.gerenciador_patrimonial.service.FrotaService;
import com.fundacao.gerenciador_patrimonial.service.ImportadorTransportesService;
import com.fundacao.gerenciador_patrimonial.service.LotacaoService;
import com.fundacao.gerenciador_patrimonial.service.ResponsavelService;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.FrotaExporter;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.FrotaExporter.Tabela;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Módulo Frota: veículos (patrimônios da categoria VEICULO) e o histórico de
 * cada um — abastecimentos, manutenções/revisões e deslocamentos.
 *
 * <ul>
 *   <li>{@code /frota} — painel + lista de veículos</li>
 *   <li>{@code /frota/novo}, {@code /frota/{id}/editar} — cadastro do veículo (+ patrimônio)</li>
 *   <li>{@code /frota/{id}} — ficha do veículo com histórico unificado</li>
 *   <li>{@code /frota/{id}/abastecimentos|manutencoes|deslocamentos/novo} — lançamentos no veículo</li>
 *   <li>{@code /frota/abastecimentos|manutencoes|deslocamentos} — listagens gerais com filtro e exportação</li>
 * </ul>
 */
@Controller
@RequestMapping("/frota")
@RequiredArgsConstructor
public class FrotaWebController {

    private final FrotaService frota;
    private final LotacaoService lotacaoService;
    private final ResponsavelService responsavelService;
    private final FrotaExporter exporter;
    private final ImportadorTransportesService importador;

    // =========================================================================
    // Veículos
    // =========================================================================

    @GetMapping
    public String painel(@RequestParam(defaultValue = "false") boolean todos, Model model) {
        model.addAttribute("resumo", frota.resumoFrota());
        model.addAttribute("veiculos", frota.listarVeiculos(!todos));
        model.addAttribute("todos", todos);
        model.addAttribute("importacaoConfigurada", importador.configurado());
        return "frota/list";
    }

    /** Painel gerencial: quem está com qual viatura, manutenções, revisões e gastos. */
    @GetMapping("/gestao")
    public String gestao(Model model) {
        model.addAttribute("g", frota.gestao());
        return "frota/gestao";
    }

    @GetMapping("/gestao/download")
    public void baixarGestao(@RequestParam(defaultValue = "xlsx") String formato, HttpServletResponse response) throws IOException {
        var g = frota.gestao();
        List<List<Object>> linhas = new ArrayList<>();
        for (var x : g.gastosPorVeiculo()) {
            linhas.add(Arrays.asList(x.placa(), x.modelo(), x.combustivel(), x.litros(), x.manutencao(), x.manutencoes(),
                    x.total(), x.kmRodados(), x.saidas(), x.custoPorKm()));
        }
        enviar(new Tabela("Gastos da frota por veículo — " + g.hoje().getYear(),
                        "Combustível R$ " + g.combustivelAno() + " · Manutenção R$ " + g.manutencaoAno() + " · " + g.kmAno() + " km rodados",
                        List.of("Placa", "Modelo", "Combustível (R$)", "Litros", "Manutenção (R$)", "Nº manutenções", "Total (R$)",
                                "Km rodados", "Saídas", "R$/km"), linhas), "gestao-frota", formato, response);
    }

    @PostMapping("/{id}/manutencao-status")
    public String manutencaoStatus(@PathVariable Long id, @RequestParam boolean emManutencao,
                                   @RequestParam(required = false) String motivo, RedirectAttributes redirect) {
        try {
            frota.marcarManutencao(id, emManutencao, motivo);
            redirect.addFlashAttribute("sucesso", emManutencao ? "Veículo marcado como em manutenção." : "Veículo de volta à operação.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/frota/" + id;
    }

    @GetMapping("/novo")
    public String novoForm(Model model) {
        if (!model.containsAttribute("veiculoForm")) {
            model.addAttribute("veiculoForm", new VeiculoRequest(null, "", "", null, null, null, null, null, null,
                    null, null, LocalDate.now(), null, null, null, null, null, null));
        }
        prepararFormVeiculo(model, null);
        return "frota/veiculo-form";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("veiculoForm") VeiculoRequest req, BindingResult binding,
                        RedirectAttributes redirect, Model model) {
        if (binding.hasErrors()) {
            prepararFormVeiculo(model, null);
            return "frota/veiculo-form";
        }
        try {
            VeiculoResponse v = frota.criarVeiculo(req);
            redirect.addFlashAttribute("sucesso", "Veículo " + v.placa() + " cadastrado (patrimônio #" + v.patrimonioId() + ").");
            return "redirect:/frota/" + v.id();
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            prepararFormVeiculo(model, null);
            return "frota/veiculo-form";
        }
    }

    @GetMapping("/{id}/editar")
    public String editarForm(@PathVariable Long id, Model model) {
        VeiculoResponse v = frota.buscarVeiculo(id);
        if (!model.containsAttribute("veiculoForm")) {
            model.addAttribute("veiculoForm", new VeiculoRequest(v.patrimonioId(), v.placa(), v.modelo(), v.marca(),
                    v.chassi(), v.renavam(), v.ano(), v.cidade(), v.combustivelPadrao(), v.kmAtual(),
                    v.numeroTombo(), v.dataCompra(), v.valorCompra(), v.conservacao(), v.notaFiscal(), v.observacao(),
                    v.lotacaoId(), v.responsavelId()));
        }
        prepararFormVeiculo(model, v);
        return "frota/veiculo-form";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id, @Valid @ModelAttribute("veiculoForm") VeiculoRequest req,
                            BindingResult binding, RedirectAttributes redirect, Model model) {
        VeiculoResponse atual = frota.buscarVeiculo(id);
        if (binding.hasErrors()) {
            prepararFormVeiculo(model, atual);
            return "frota/veiculo-form";
        }
        try {
            frota.atualizarVeiculo(id, req);
            redirect.addFlashAttribute("sucesso", "Veículo atualizado.");
            return "redirect:/frota/" + id;
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            prepararFormVeiculo(model, atual);
            return "frota/veiculo-form";
        }
    }

    @PostMapping("/{id}/ativo")
    public String alterarAtivo(@PathVariable Long id, @RequestParam boolean ativo, RedirectAttributes redirect) {
        frota.alterarAtivo(id, ativo);
        redirect.addFlashAttribute("sucesso", ativo ? "Veículo reativado." : "Veículo desativado (sai dos selects de lançamento).");
        return "redirect:/frota/" + id;
    }

    @GetMapping("/{id}")
    public String ficha(@PathVariable Long id,
                        @RequestParam(required = false) String tipo,
                        Model model) {
        VeiculoResponse v = frota.buscarVeiculo(id);
        List<HistoricoVeiculoItem> historico = frota.historico(id);
        HistoricoVeiculoItem.Tipo filtro = parseTipo(tipo);
        if (filtro != null) historico = historico.stream().filter(h -> h.tipo() == filtro).toList();

        model.addAttribute("veiculo", v);
        model.addAttribute("resumo", frota.resumo(id));
        model.addAttribute("historico", historico);
        model.addAttribute("tipo", filtro != null ? filtro.name() : "TODOS");
        model.addAttribute("hoje", LocalDate.now());
        model.addAttribute("finalizarForm", new FinalizarDeslocamentoRequest(null, null));
        return "frota/veiculo";
    }

    @GetMapping("/{id}/historico/download")
    public void baixarHistorico(@PathVariable Long id, @RequestParam(defaultValue = "xlsx") String formato,
                                HttpServletResponse response) throws IOException {
        VeiculoResponse v = frota.buscarVeiculo(id);
        List<List<Object>> linhas = new ArrayList<>();
        for (HistoricoVeiculoItem h : frota.historico(id)) {
            linhas.add(Arrays.asList(h.data(), h.hora(), h.titulo(), h.detalhe(), h.km(), h.valor(), h.motorista(),
                    h.emAndamento() ? "Em andamento" : ""));
        }
        enviar(new Tabela("Histórico do veículo " + v.placa() + " - " + v.modelo(),
                        "Patrimônio #" + v.patrimonioId() + (v.numeroTombo() != null ? " · tombo " + v.numeroTombo() : ""),
                        List.of("Data", "Hora", "Lançamento", "Detalhe", "Km", "Valor (R$)", "Motorista", "Situação"), linhas),
                "historico-" + v.placa(), formato, response);
    }

    // =========================================================================
    // Abastecimentos
    // =========================================================================

    @GetMapping("/abastecimentos")
    public String abastecimentos(@ModelAttribute("filtro") FiltroFrota filtro,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "25") int size, Model model) {
        Page<AbastecimentoResponse> pagina = frota.listarAbastecimentos(filtro, paginacao(page, size, "data"));
        model.addAttribute("pagina", pagina);
        model.addAttribute("veiculos", frota.veiculosParaSelect());
        return "frota/abastecimentos";
    }

    @GetMapping("/abastecimentos/download")
    public void baixarAbastecimentos(@ModelAttribute("filtro") FiltroFrota filtro,
                                     @RequestParam(defaultValue = "xlsx") String formato,
                                     HttpServletResponse response) throws IOException {
        List<List<Object>> linhas = new ArrayList<>();
        for (AbastecimentoResponse a : frota.listarAbastecimentos(filtro, Pageable.unpaged()).getContent()) {
            linhas.add(Arrays.asList(a.data(), a.placa() + " - " + a.modelo(), a.km(), a.combustivel().getRotulo(),
                    a.quantidadeLitros(), a.valor(), a.precoLitro(), a.postoCidade(), a.motorista()));
        }
        enviar(new Tabela("Abastecimentos", periodo(filtro),
                        List.of("Data", "Veículo", "Km", "Combustível", "Litros", "Valor (R$)", "R$/litro", "Posto / cidade", "Motorista"),
                        linhas), "abastecimentos", formato, response);
    }

    @GetMapping({"/{id}/abastecimentos/novo", "/abastecimentos/novo"})
    public String abastecimentoForm(@PathVariable(required = false) Long id, Model model) {
        if (!model.containsAttribute("abastecimentoForm")) {
            VeiculoResponse v = id != null ? frota.buscarVeiculo(id) : null;
            model.addAttribute("abastecimentoForm", new AbastecimentoRequest(id, LocalDate.now(),
                    v != null ? v.kmAtual() : null, v != null ? v.combustivelPadrao() : null,
                    null, null, null, FrotaService.nomeUsuarioAtual(), null));
        }
        prepararFormLancamento(model, id, "abastecimento", null);
        return "frota/abastecimento-form";
    }

    @PostMapping("/abastecimentos")
    public String criarAbastecimento(@Valid @ModelAttribute("abastecimentoForm") AbastecimentoRequest req,
                                     BindingResult binding,
                                     @RequestParam(value = "notaFiscalArquivo", required = false) MultipartFile nf,
                                     @AuthenticationPrincipal UsuarioAutenticado logado,
                                     RedirectAttributes redirect, Model model) {
        if (req.veiculoId() == null) binding.rejectValue("veiculoId", "obrigatorio", "Selecione o veículo");
        String faltaNf = validarNfObrigatoria(logado, nf, false);
        if (binding.hasErrors() || faltaNf != null) {
            if (faltaNf != null) model.addAttribute("erro", faltaNf);
            prepararFormLancamento(model, req.veiculoId(), "abastecimento", null);
            return "frota/abastecimento-form";
        }
        try {
            AbastecimentoResponse a = frota.registrarAbastecimento(req.veiculoId(), req);
            if (nf != null && !nf.isEmpty()) frota.anexarNotaFiscal(HistoricoVeiculoItem.Tipo.ABASTECIMENTO, a.id(), nf);
            redirect.addFlashAttribute("sucesso", "Abastecimento registrado para " + a.placa() + ".");
            return "redirect:/frota/" + req.veiculoId();
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            prepararFormLancamento(model, req.veiculoId(), "abastecimento", null);
            return "frota/abastecimento-form";
        }
    }

    @GetMapping("/abastecimentos/{regId}/editar")
    public String editarAbastecimento(@PathVariable Long regId, Model model) {
        AbastecimentoResponse a = frota.buscarAbastecimento(regId);
        if (!model.containsAttribute("abastecimentoForm")) {
            model.addAttribute("abastecimentoForm", new AbastecimentoRequest(a.veiculoId(), a.data(), a.km(),
                    a.combustivel(), a.quantidadeLitros(), a.valor(), a.postoCidade(), a.motorista(), a.observacao()));
        }
        prepararFormLancamento(model, a.veiculoId(), "abastecimento", regId);
        model.addAttribute("nfAtual", a.possuiNotaFiscal() ? a.nfNomeOriginal() : null);
        return "frota/abastecimento-form";
    }

    @PostMapping("/abastecimentos/{regId}")
    public String atualizarAbastecimento(@PathVariable Long regId,
                                         @Valid @ModelAttribute("abastecimentoForm") AbastecimentoRequest req,
                                         BindingResult binding,
                                         @RequestParam(value = "notaFiscalArquivo", required = false) MultipartFile nf,
                                         RedirectAttributes redirect, Model model) {
        if (binding.hasErrors()) {
            prepararFormLancamento(model, req.veiculoId(), "abastecimento", regId);
            return "frota/abastecimento-form";
        }
        try {
            AbastecimentoResponse a = frota.atualizarAbastecimento(regId, req);
            if (nf != null && !nf.isEmpty()) frota.anexarNotaFiscal(HistoricoVeiculoItem.Tipo.ABASTECIMENTO, regId, nf);
            redirect.addFlashAttribute("sucesso", "Abastecimento atualizado.");
            return "redirect:/frota/" + a.veiculoId() + "?tipo=ABASTECIMENTO";
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            prepararFormLancamento(model, req.veiculoId(), "abastecimento", regId);
            return "frota/abastecimento-form";
        }
    }

    @PostMapping("/abastecimentos/{regId}/excluir")
    public String excluirAbastecimento(@PathVariable Long regId, RedirectAttributes redirect) {
        Long veiculoId = frota.buscarAbastecimento(regId).veiculoId();
        frota.excluirAbastecimento(regId);
        redirect.addFlashAttribute("sucesso", "Abastecimento excluído.");
        return "redirect:/frota/" + veiculoId + "?tipo=ABASTECIMENTO";
    }

    // =========================================================================
    // Manutenções / revisões
    // =========================================================================

    @GetMapping("/manutencoes")
    public String manutencoes(@ModelAttribute("filtro") FiltroFrota filtro,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "25") int size, Model model) {
        Page<ManutencaoVeiculoResponse> pagina = frota.listarManutencoes(filtro, paginacao(page, size, "data"));
        model.addAttribute("pagina", pagina);
        model.addAttribute("veiculos", frota.veiculosParaSelect());
        return "frota/manutencoes";
    }

    @GetMapping("/manutencoes/download")
    public void baixarManutencoes(@ModelAttribute("filtro") FiltroFrota filtro,
                                  @RequestParam(defaultValue = "xlsx") String formato,
                                  HttpServletResponse response) throws IOException {
        List<List<Object>> linhas = new ArrayList<>();
        for (ManutencaoVeiculoResponse m : frota.listarManutencoes(filtro, Pageable.unpaged()).getContent()) {
            linhas.add(Arrays.asList(m.data(), m.placa() + " - " + m.modelo(), m.servico().getRotulo(), m.km(),
                    m.descricao(), m.fornecedor(), m.notaFiscal(), m.valor(), m.proximaRevisaoKm(), m.proximaRevisaoData(), m.motorista()));
        }
        enviar(new Tabela("Manutenções e revisões", periodo(filtro),
                        List.of("Data", "Veículo", "Serviço", "Km", "Descrição", "Fornecedor", "NF", "Valor (R$)",
                                "Próx. revisão (km)", "Próx. revisão (data)", "Responsável"),
                        linhas), "manutencoes", formato, response);
    }

    @GetMapping({"/{id}/manutencoes/novo", "/manutencoes/novo"})
    public String manutencaoForm(@PathVariable(required = false) Long id, Model model) {
        if (!model.containsAttribute("manutencaoForm")) {
            VeiculoResponse v = id != null ? frota.buscarVeiculo(id) : null;
            model.addAttribute("manutencaoForm", new ManutencaoVeiculoRequest(id, LocalDate.now(), null,
                    v != null ? v.kmAtual() : null, null, null, null, null, null, null, FrotaService.nomeUsuarioAtual()));
        }
        prepararFormLancamento(model, id, "manutencao", null);
        return "frota/manutencao-form";
    }

    @PostMapping("/manutencoes")
    public String criarManutencao(@Valid @ModelAttribute("manutencaoForm") ManutencaoVeiculoRequest req,
                                  BindingResult binding,
                                  @RequestParam(value = "notaFiscalArquivo", required = false) MultipartFile nf,
                                  @AuthenticationPrincipal UsuarioAutenticado logado,
                                  RedirectAttributes redirect, Model model) {
        if (req.veiculoId() == null) binding.rejectValue("veiculoId", "obrigatorio", "Selecione o veículo");
        String faltaNf = validarNfObrigatoria(logado, nf, false);
        if (binding.hasErrors() || faltaNf != null) {
            if (faltaNf != null) model.addAttribute("erro", faltaNf);
            prepararFormLancamento(model, req.veiculoId(), "manutencao", null);
            return "frota/manutencao-form";
        }
        try {
            ManutencaoVeiculoResponse m = frota.registrarManutencao(req.veiculoId(), req);
            if (nf != null && !nf.isEmpty()) frota.anexarNotaFiscal(HistoricoVeiculoItem.Tipo.MANUTENCAO, m.id(), nf);
            redirect.addFlashAttribute("sucesso", m.servico().getRotulo() + " registrada para " + m.placa() + ".");
            return "redirect:/frota/" + req.veiculoId();
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            prepararFormLancamento(model, req.veiculoId(), "manutencao", null);
            return "frota/manutencao-form";
        }
    }

    @GetMapping("/manutencoes/{regId}/editar")
    public String editarManutencao(@PathVariable Long regId, Model model) {
        ManutencaoVeiculoResponse m = frota.buscarManutencao(regId);
        if (!model.containsAttribute("manutencaoForm")) {
            model.addAttribute("manutencaoForm", new ManutencaoVeiculoRequest(m.veiculoId(), m.data(), m.servico(),
                    m.km(), m.descricao(), m.valor(), m.fornecedor(), m.notaFiscal(), m.proximaRevisaoKm(),
                    m.proximaRevisaoData(), m.motorista()));
        }
        prepararFormLancamento(model, m.veiculoId(), "manutencao", regId);
        model.addAttribute("nfAtual", m.possuiNotaFiscal() ? m.nfNomeOriginal() : null);
        return "frota/manutencao-form";
    }

    @PostMapping("/manutencoes/{regId}")
    public String atualizarManutencao(@PathVariable Long regId,
                                      @Valid @ModelAttribute("manutencaoForm") ManutencaoVeiculoRequest req,
                                      BindingResult binding,
                                      @RequestParam(value = "notaFiscalArquivo", required = false) MultipartFile nf,
                                      RedirectAttributes redirect, Model model) {
        if (binding.hasErrors()) {
            prepararFormLancamento(model, req.veiculoId(), "manutencao", regId);
            return "frota/manutencao-form";
        }
        try {
            ManutencaoVeiculoResponse m = frota.atualizarManutencao(regId, req);
            if (nf != null && !nf.isEmpty()) frota.anexarNotaFiscal(HistoricoVeiculoItem.Tipo.MANUTENCAO, regId, nf);
            redirect.addFlashAttribute("sucesso", "Manutenção atualizada.");
            return "redirect:/frota/" + m.veiculoId() + "?tipo=MANUTENCAO";
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            prepararFormLancamento(model, req.veiculoId(), "manutencao", regId);
            return "frota/manutencao-form";
        }
    }

    @PostMapping("/manutencoes/{regId}/excluir")
    public String excluirManutencao(@PathVariable Long regId, RedirectAttributes redirect) {
        Long veiculoId = frota.buscarManutencao(regId).veiculoId();
        frota.excluirManutencao(regId);
        redirect.addFlashAttribute("sucesso", "Manutenção excluída.");
        return "redirect:/frota/" + veiculoId + "?tipo=MANUTENCAO";
    }

    // =========================================================================
    // Deslocamentos
    // =========================================================================

    @GetMapping("/deslocamentos")
    public String deslocamentos(@ModelAttribute("filtro") FiltroFrota filtro,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "25") int size, Model model) {
        Page<DeslocamentoResponse> pagina = frota.listarDeslocamentos(filtro, paginacao(page, size, "dataViagem"));
        model.addAttribute("pagina", pagina);
        model.addAttribute("veiculos", frota.veiculosParaSelect());
        model.addAttribute("finalizarForm", new FinalizarDeslocamentoRequest(null, null));
        return "frota/deslocamentos";
    }

    @GetMapping("/deslocamentos/download")
    public void baixarDeslocamentos(@ModelAttribute("filtro") FiltroFrota filtro,
                                    @RequestParam(defaultValue = "xlsx") String formato,
                                    HttpServletResponse response) throws IOException {
        List<List<Object>> linhas = new ArrayList<>();
        for (DeslocamentoResponse d : frota.listarDeslocamentos(filtro, Pageable.unpaged()).getContent()) {
            linhas.add(Arrays.asList(d.dataViagem(), d.placa() + " - " + d.modelo(), d.destino(), d.motivo(), d.titular(),
                    d.horaSaida(), d.kmInicial(), d.horaChegada(), d.kmFinal(), d.kmRodados(), d.motorista(), d.lotacao(),
                    d.emAndamento() ? "Em andamento" : "Finalizado"));
        }
        enviar(new Tabela("Deslocamentos", periodo(filtro),
                        List.of("Data", "Veículo", "Destino", "Motivo", "Titular", "Saída", "Km inicial", "Chegada",
                                "Km final", "Km rodados", "Motorista", "Lotação", "Situação"),
                        linhas), "deslocamentos", formato, response);
    }

    @GetMapping({"/{id}/deslocamentos/novo", "/deslocamentos/novo"})
    public String deslocamentoForm(@PathVariable(required = false) Long id, Model model) {
        if (!model.containsAttribute("deslocamentoForm")) {
            VeiculoResponse v = id != null ? frota.buscarVeiculo(id) : null;
            model.addAttribute("deslocamentoForm", new DeslocamentoRequest(id, LocalDate.now(), "", "", null, null,
                    java.time.LocalTime.now().withSecond(0).withNano(0), v != null ? v.kmAtual() : null, null, null,
                    FrotaService.nomeUsuarioAtual(), null, null));
        }
        prepararFormLancamento(model, id, "deslocamento", null);
        return "frota/deslocamento-form";
    }

    @PostMapping("/deslocamentos")
    public String criarDeslocamento(@Valid @ModelAttribute("deslocamentoForm") DeslocamentoRequest req,
                                    BindingResult binding, RedirectAttributes redirect, Model model) {
        if (req.veiculoId() == null) binding.rejectValue("veiculoId", "obrigatorio", "Selecione o veículo");
        if (binding.hasErrors()) {
            prepararFormLancamento(model, req.veiculoId(), "deslocamento", null);
            return "frota/deslocamento-form";
        }
        try {
            DeslocamentoResponse d = frota.registrarDeslocamento(req.veiculoId(), req);
            redirect.addFlashAttribute("sucesso", "Saída registrada para " + d.placa()
                    + (d.emAndamento() ? " — veículo na rua. Registre a chegada ao retornar." : "."));
            return "redirect:/frota/" + req.veiculoId();
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            prepararFormLancamento(model, req.veiculoId(), "deslocamento", null);
            return "frota/deslocamento-form";
        }
    }

    @GetMapping("/deslocamentos/{regId}/editar")
    public String editarDeslocamento(@PathVariable Long regId, Model model) {
        DeslocamentoResponse d = frota.buscarDeslocamento(regId);
        if (!model.containsAttribute("deslocamentoForm")) {
            model.addAttribute("deslocamentoForm", new DeslocamentoRequest(d.veiculoId(), d.dataViagem(), d.destino(),
                    d.motivo(), d.titular(), d.assistido(), d.horaSaida(), d.kmInicial(), d.horaChegada(), d.kmFinal(),
                    d.motorista(), d.lotacao(), d.observacao()));
        }
        prepararFormLancamento(model, d.veiculoId(), "deslocamento", regId);
        return "frota/deslocamento-form";
    }

    @PostMapping("/deslocamentos/{regId}")
    public String atualizarDeslocamento(@PathVariable Long regId,
                                        @Valid @ModelAttribute("deslocamentoForm") DeslocamentoRequest req,
                                        BindingResult binding, RedirectAttributes redirect, Model model) {
        if (binding.hasErrors()) {
            prepararFormLancamento(model, req.veiculoId(), "deslocamento", regId);
            return "frota/deslocamento-form";
        }
        try {
            DeslocamentoResponse d = frota.atualizarDeslocamento(regId, req);
            redirect.addFlashAttribute("sucesso", "Deslocamento atualizado.");
            return "redirect:/frota/" + d.veiculoId() + "?tipo=DESLOCAMENTO";
        } catch (RuntimeException e) {
            model.addAttribute("erro", e.getMessage());
            prepararFormLancamento(model, req.veiculoId(), "deslocamento", regId);
            return "frota/deslocamento-form";
        }
    }

    @PostMapping("/deslocamentos/{regId}/finalizar")
    public String finalizarDeslocamento(@PathVariable Long regId,
                                        @Valid @ModelAttribute("finalizarForm") FinalizarDeslocamentoRequest req,
                                        BindingResult binding,
                                        @RequestParam(required = false) String voltar,
                                        RedirectAttributes redirect) {
        Long veiculoId = frota.buscarDeslocamento(regId).veiculoId();
        String destino = "lista".equals(voltar) ? "redirect:/frota/deslocamentos" : "redirect:/frota/" + veiculoId;
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("erro", "Informe o km final para registrar a chegada.");
            return destino;
        }
        try {
            DeslocamentoResponse d = frota.finalizarDeslocamento(regId, req);
            redirect.addFlashAttribute("sucesso", "Chegada registrada: " + d.kmRodados() + " km rodados.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return destino;
    }

    @PostMapping("/deslocamentos/{regId}/excluir")
    public String excluirDeslocamento(@PathVariable Long regId, RedirectAttributes redirect) {
        Long veiculoId = frota.buscarDeslocamento(regId).veiculoId();
        frota.excluirDeslocamento(regId);
        redirect.addFlashAttribute("sucesso", "Deslocamento excluído.");
        return "redirect:/frota/" + veiculoId + "?tipo=DESLOCAMENTO";
    }

    // =========================================================================
    // Nota fiscal (download inline)
    // =========================================================================

    @GetMapping({"/abastecimentos/{regId}/nota-fiscal", "/manutencoes/{regId}/nota-fiscal"})
    public ResponseEntity<Resource> notaFiscal(@PathVariable Long regId, jakarta.servlet.http.HttpServletRequest request) {
        HistoricoVeiculoItem.Tipo tipo = request.getRequestURI().contains("/manutencoes/")
                ? HistoricoVeiculoItem.Tipo.MANUTENCAO : HistoricoVeiculoItem.Tipo.ABASTECIMENTO;
        FrotaService.NotaFiscalArquivo nf = frota.notaFiscal(tipo, regId);
        String nome = nf.nomeOriginal() != null ? nf.nomeOriginal() : "nota-fiscal";
        String nomeAscii = nome.replaceAll("[^\\x20-\\x7E]", "_");
        String nomeUtf8 = java.net.URLEncoder.encode(nome, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        try { if (nf.contentType() != null) mt = MediaType.parseMediaType(nf.contentType()); } catch (RuntimeException ignored) { }
        return ResponseEntity.ok()
                .contentType(mt)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nomeAscii + "\"; filename*=UTF-8''" + nomeUtf8)
                .body(nf.recurso());
    }

    /**
     * Motorista só registra abastecimento/manutenção com a nota fiscal (PDF ou foto).
     * ADMINISTRADOR/FISCAL podem lançar sem (registros antigos, NF entregue em papel).
     */
    private static String validarNfObrigatoria(UsuarioAutenticado logado, MultipartFile nf, boolean editando) {
        boolean motorista = logado != null && logado.getUsuario().getPerfil() == Perfil.MOTORISTA;
        if (!motorista || editando) return null;
        if (nf == null || nf.isEmpty()) return "Anexe a nota fiscal (PDF ou foto) para registrar o lançamento.";
        return null;
    }

    // =========================================================================
    // Importação do Transportes (ADMIN)
    // =========================================================================

    @PostMapping("/importar-transportes")
    public String importarTransportes(RedirectAttributes redirect) {
        try {
            var r = importador.importar();
            redirect.addFlashAttribute("sucesso",
                    "Importação concluída: %d veículo(s) novo(s), %d abastecimento(s), %d manutenção(ões), %d deslocamento(s)."
                            .formatted(r.veiculosNovos(), r.abastecimentos(), r.manutencoes(), r.deslocamentos()));
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/frota";
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private void prepararFormVeiculo(Model model, VeiculoResponse existente) {
        model.addAttribute("veiculo", existente);
        model.addAttribute("editando", existente != null);
        model.addAttribute("lotacoes", lotacaoService.listarParaSelect());
        model.addAttribute("responsaveis", responsavelService.listarParaSelect());
        model.addAttribute("conservacoes", Conservacao.values());
        model.addAttribute("combustiveis", Combustivel.values());
        if (existente == null) model.addAttribute("patrimoniosDisponiveis", frota.patrimoniosVeiculoSemFicha());
    }

    private void prepararFormLancamento(Model model, Long veiculoId, String tipo, Long regId) {
        model.addAttribute("veiculo", veiculoId != null ? frota.buscarVeiculo(veiculoId) : null);
        model.addAttribute("veiculos", frota.veiculosParaSelect());
        model.addAttribute("editando", regId != null);
        model.addAttribute("regId", regId);
        model.addAttribute("combustiveis", Combustivel.values());
        model.addAttribute("servicos", TipoServicoVeiculo.values());
        model.addAttribute("motivos", FrotaCatalog.MOTIVOS_DESLOCAMENTO);
        model.addAttribute("lotacoesFrota", FrotaCatalog.LOTACOES);
        model.addAttribute("tipoLancamento", tipo);
    }

    private static Pageable paginacao(int page, int size, String campoData) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Order.desc(campoData), Sort.Order.desc("id")));
    }

    private static HistoricoVeiculoItem.Tipo parseTipo(String tipo) {
        if (tipo == null || tipo.isBlank() || "TODOS".equalsIgnoreCase(tipo)) return null;
        try {
            return HistoricoVeiculoItem.Tipo.valueOf(tipo.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String periodo(FiltroFrota f) {
        if (f.de() == null && f.ate() == null) return "todos os registros";
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return "período " + (f.de() != null ? f.de().format(fmt) : "…") + " a " + (f.ate() != null ? f.ate().format(fmt) : "…");
    }

    private void enviar(Tabela tabela, String nomeBase, String formato, HttpServletResponse response) throws IOException {
        String data = LocalDate.now().toString();
        if ("pdf".equalsIgnoreCase(formato)) {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + nomeBase + "-" + data + ".pdf\"");
            exporter.pdf(tabela, response.getOutputStream());
        } else {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + nomeBase + "-" + data + ".xlsx\"");
            exporter.xlsx(tabela, response.getOutputStream());
        }
        response.flushBuffer();
    }
}

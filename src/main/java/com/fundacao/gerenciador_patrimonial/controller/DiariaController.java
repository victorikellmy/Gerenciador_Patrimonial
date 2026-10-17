package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import com.fundacao.gerenciador_patrimonial.dto.request.DiariaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroFrota;
import com.fundacao.gerenciador_patrimonial.dto.request.NivelDiariaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.ProfissionalRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.DiariaHistoricoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.DiariaResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.NivelDiariaResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.ProfissionalResponse;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import com.fundacao.gerenciador_patrimonial.service.DiariaService;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.DiariaPdfExporter;
import com.fundacao.gerenciador_patrimonial.service.report.exporter.FrotaExporter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** API REST das diárias (SPA e app). Só delega ao {@link DiariaService}. */
@RestController
@RequestMapping("/api/diarias")
@RequiredArgsConstructor
public class DiariaController {

    private final DiariaService service;
    private final DiariaPdfExporter pdf;
    private final FrotaExporter exporter;

    public record Transicao(String observacao, @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataPagamento) {}

    @GetMapping
    public Page<DiariaResponse> listar(@RequestParam(required = false) StatusDiaria status,
                                       @RequestParam(defaultValue = "false") boolean padraoDoPerfil,
                                       @ModelAttribute FiltroFrota filtro,
                                       @AuthenticationPrincipal UsuarioAutenticado logado,
                                       @PageableDefault(size = 25) Pageable pageable) {
        StatusDiaria st = status != null ? status : (padraoDoPerfil ? DiariaService.statusPadrao(logado) : null);
        return service.listar(st, filtro, pageable);
    }

    @GetMapping("/contagens")
    public Map<String, Long> contagens() {
        Map<String, Long> m = new java.util.LinkedHashMap<>();
        for (StatusDiaria s : StatusDiaria.values()) m.put(s.name(), service.contar(s));
        return m;
    }

    @GetMapping("/{id:\\d+}")
    public DiariaResponse buscar(@PathVariable Long id) { return service.buscar(id); }

    @GetMapping("/{id:\\d+}/historico")
    public List<DiariaHistoricoResponse> historico(@PathVariable Long id) { return service.historico(id); }

    @GetMapping("/{id:\\d+}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] bytes = pdf.solicitacaoDiaria(service.buscar(id), service.historico(id));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"diaria-" + id + ".pdf\"").body(bytes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiariaResponse criar(@Valid @RequestBody DiariaRequest req) { return service.criar(req); }

    @PutMapping("/{id:\\d+}")
    public DiariaResponse atualizar(@PathVariable Long id, @Valid @RequestBody DiariaRequest req) { return service.atualizar(id, req); }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) { service.excluir(id); return ResponseEntity.noContent().build(); }

    @PostMapping("/{id:\\d+}/confirmar")
    public DiariaResponse confirmar(@PathVariable Long id, @RequestBody(required = false) Transicao t) {
        return service.transicionar(id, StatusDiaria.CONFIRMADO, t != null ? t.observacao() : null, null);
    }

    @PostMapping("/{id:\\d+}/deferir")
    public DiariaResponse deferir(@PathVariable Long id, @RequestBody(required = false) Transicao t) {
        return service.transicionar(id, StatusDiaria.DEFERIDO, t != null ? t.observacao() : null, null);
    }

    @PostMapping("/{id:\\d+}/indeferir")
    public DiariaResponse indeferir(@PathVariable Long id, @RequestBody Transicao t) {
        return service.transicionar(id, StatusDiaria.INDEFERIDO, t.observacao(), null);
    }

    @PostMapping("/{id:\\d+}/pagar")
    public DiariaResponse pagar(@PathVariable Long id, @RequestBody(required = false) Transicao t) {
        return service.transicionar(id, StatusDiaria.PAGO, t != null ? t.observacao() : null, t != null ? t.dataPagamento() : null);
    }

    @GetMapping("/valores/{profissionalId}")
    public ProfissionalResponse valores(@PathVariable Long profissionalId) { return service.valoresDoProfissional(profissionalId); }

    // --- pagas ---

    @GetMapping("/pagas")
    public Map<String, Object> pagas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        LocalDate[] p = periodo(de, ate);
        return Map.of("de", p[0], "ate", p[1], "lista", service.pagasNoPeriodo(p[0], p[1]), "total", service.totalPagoNoPeriodo(p[0], p[1]));
    }

    @GetMapping("/pagas/download")
    public void baixarPagas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                            @RequestParam(defaultValue = "pdf") String formato,
                            @AuthenticationPrincipal UsuarioAutenticado logado, HttpServletResponse response) throws IOException {
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

    // --- profissionais ---

    @GetMapping("/profissionais")
    public List<ProfissionalResponse> profissionais(@RequestParam(required = false) String busca,
                                                    @RequestParam(defaultValue = "false") boolean ativos) {
        return ativos ? service.profissionaisAtivos() : service.listarProfissionais(busca);
    }

    @GetMapping("/profissionais/{id}")
    public ProfissionalResponse profissional(@PathVariable Long id) { return service.buscarProfissional(id); }

    @PostMapping("/profissionais")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfissionalResponse criarProfissional(@Valid @RequestBody ProfissionalRequest req) { return service.salvarProfissional(null, req); }

    @PutMapping("/profissionais/{id}")
    public ProfissionalResponse atualizarProfissional(@PathVariable Long id, @Valid @RequestBody ProfissionalRequest req) {
        return service.salvarProfissional(id, req);
    }

    @PostMapping("/profissionais/{id}/ativo")
    public ProfissionalResponse ativoProfissional(@PathVariable Long id, @RequestParam boolean ativo) {
        service.alterarAtivoProfissional(id, ativo);
        return service.buscarProfissional(id);
    }

    // --- níveis ---

    @GetMapping("/niveis")
    public List<NivelDiariaResponse> niveis(@RequestParam(defaultValue = "false") boolean ativos) {
        return ativos ? service.niveisAtivos() : service.listarNiveis();
    }

    @PostMapping("/niveis")
    @ResponseStatus(HttpStatus.CREATED)
    public NivelDiariaResponse criarNivel(@Valid @RequestBody NivelDiariaRequest req) { return service.salvarNivel(null, req); }

    @PostMapping("/niveis/{id}")
    public NivelDiariaResponse atualizarNivel(@PathVariable Long id, @Valid @RequestBody NivelDiariaRequest req) { return service.salvarNivel(id, req); }

    @PostMapping("/niveis/{id}/ativo")
    public List<NivelDiariaResponse> ativoNivel(@PathVariable Long id, @RequestParam boolean ativo) {
        service.alterarAtivoNivel(id, ativo);
        return service.listarNiveis();
    }

    private static LocalDate[] periodo(LocalDate de, LocalDate ate) {
        LocalDate hoje = LocalDate.now();
        return new LocalDate[]{de != null ? de : hoje.withDayOfMonth(1), ate != null ? ate : hoje};
    }
}

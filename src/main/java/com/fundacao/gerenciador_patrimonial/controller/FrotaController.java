package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.domain.catalog.FrotaCatalog;
import com.fundacao.gerenciador_patrimonial.domain.enums.Combustivel;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoServicoVeiculo;
import com.fundacao.gerenciador_patrimonial.dto.request.AbastecimentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.DeslocamentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroFrota;
import com.fundacao.gerenciador_patrimonial.dto.request.FinalizarDeslocamentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.ManutencaoVeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.VeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.AbastecimentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.DeslocamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.FrotaResumo;
import com.fundacao.gerenciador_patrimonial.dto.response.HistoricoVeiculoItem;
import com.fundacao.gerenciador_patrimonial.dto.response.ManutencaoVeiculoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.VeiculoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.VeiculoResumo;
import com.fundacao.gerenciador_patrimonial.service.FrotaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * API REST da frota (app Android). Só delega ao {@link FrotaService}.
 *
 * <pre>
 * GET  /api/frota/veiculos?todos=false          lista
 * GET  /api/frota/veiculos/{id}                 ficha (veículo + resumo)
 * GET  /api/frota/veiculos/{id}/historico       histórico unificado
 * POST /api/frota/veiculos                      cadastra (ADMIN/FISCAL — regra na SecurityConfig)
 * POST /api/frota/veiculos/{id}/abastecimentos | manutencoes | deslocamentos
 * POST /api/frota/deslocamentos/{id}/finalizar
 * GET  /api/frota/abastecimentos | manutencoes | deslocamentos   (filtro + paginação)
 * GET  /api/frota/catalogos                     combustíveis, serviços, motivos, lotações
 * </pre>
 */
@RestController
@RequestMapping("/api/frota")
@RequiredArgsConstructor
public class FrotaController {

    private final FrotaService frota;

    public record Ficha(VeiculoResponse veiculo, VeiculoResumo resumo) {}

    @GetMapping("/resumo")
    public FrotaResumo resumo() { return frota.resumoFrota(); }

    @GetMapping("/veiculos")
    public List<VeiculoResponse> veiculos(@RequestParam(defaultValue = "false") boolean todos) {
        return frota.listarVeiculos(!todos);
    }

    @GetMapping("/veiculos/{id}")
    public Ficha ficha(@PathVariable Long id) {
        return new Ficha(frota.buscarVeiculo(id), frota.resumo(id));
    }

    @GetMapping("/veiculos/{id}/historico")
    public List<HistoricoVeiculoItem> historico(@PathVariable Long id) { return frota.historico(id); }

    @PostMapping("/veiculos")
    public ResponseEntity<VeiculoResponse> criarVeiculo(@Valid @RequestBody VeiculoRequest req) {
        VeiculoResponse v = frota.criarVeiculo(req);
        return ResponseEntity.created(URI.create("/api/frota/veiculos/" + v.id())).body(v);
    }

    @PutMapping("/veiculos/{id}")
    public VeiculoResponse atualizarVeiculo(@PathVariable Long id, @Valid @RequestBody VeiculoRequest req) {
        return frota.atualizarVeiculo(id, req);
    }

    // --- lançamentos ---

    @PostMapping("/veiculos/{id}/abastecimentos")
    @ResponseStatus(HttpStatus.CREATED)
    public AbastecimentoResponse abastecer(@PathVariable Long id, @Valid @RequestBody AbastecimentoRequest req) {
        return frota.registrarAbastecimento(id, req);
    }

    @PostMapping("/veiculos/{id}/manutencoes")
    @ResponseStatus(HttpStatus.CREATED)
    public ManutencaoVeiculoResponse manutencao(@PathVariable Long id, @Valid @RequestBody ManutencaoVeiculoRequest req) {
        return frota.registrarManutencao(id, req);
    }

    @PostMapping("/veiculos/{id}/deslocamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public DeslocamentoResponse deslocamento(@PathVariable Long id, @Valid @RequestBody DeslocamentoRequest req) {
        return frota.registrarDeslocamento(id, req);
    }

    @PostMapping("/deslocamentos/{id}/finalizar")
    public DeslocamentoResponse finalizar(@PathVariable Long id, @Valid @RequestBody FinalizarDeslocamentoRequest req) {
        return frota.finalizarDeslocamento(id, req);
    }

    @PutMapping("/abastecimentos/{id}")
    public AbastecimentoResponse atualizarAbastecimento(@PathVariable Long id, @Valid @RequestBody AbastecimentoRequest req) {
        return frota.atualizarAbastecimento(id, req);
    }

    @PutMapping("/manutencoes/{id}")
    public ManutencaoVeiculoResponse atualizarManutencao(@PathVariable Long id, @Valid @RequestBody ManutencaoVeiculoRequest req) {
        return frota.atualizarManutencao(id, req);
    }

    @PutMapping("/deslocamentos/{id}")
    public DeslocamentoResponse atualizarDeslocamento(@PathVariable Long id, @Valid @RequestBody DeslocamentoRequest req) {
        return frota.atualizarDeslocamento(id, req);
    }

    // --- nota fiscal (multipart "arquivo": PDF ou foto). Obrigatória para MOTORISTA no app. ---

    @PostMapping(value = "/abastecimentos/{id}/nota-fiscal", consumes = "multipart/form-data")
    public AbastecimentoResponse nfAbastecimento(@PathVariable Long id,
                                                 @RequestParam("arquivo") org.springframework.web.multipart.MultipartFile arquivo) {
        frota.anexarNotaFiscal(HistoricoVeiculoItem.Tipo.ABASTECIMENTO, id, arquivo);
        return frota.buscarAbastecimento(id);
    }

    @PostMapping(value = "/manutencoes/{id}/nota-fiscal", consumes = "multipart/form-data")
    public ManutencaoVeiculoResponse nfManutencao(@PathVariable Long id,
                                                  @RequestParam("arquivo") org.springframework.web.multipart.MultipartFile arquivo) {
        frota.anexarNotaFiscal(HistoricoVeiculoItem.Tipo.MANUTENCAO, id, arquivo);
        return frota.buscarManutencao(id);
    }

    @GetMapping({"/abastecimentos/{id}/nota-fiscal", "/manutencoes/{id}/nota-fiscal"})
    public ResponseEntity<org.springframework.core.io.Resource> notaFiscal(@PathVariable Long id,
                                                                            jakarta.servlet.http.HttpServletRequest request) {
        HistoricoVeiculoItem.Tipo tipo = request.getRequestURI().contains("/manutencoes/")
                ? HistoricoVeiculoItem.Tipo.MANUTENCAO : HistoricoVeiculoItem.Tipo.ABASTECIMENTO;
        FrotaService.NotaFiscalArquivo nf = frota.notaFiscal(tipo, id);
        org.springframework.http.MediaType mt = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
        try { if (nf.contentType() != null) mt = org.springframework.http.MediaType.parseMediaType(nf.contentType()); } catch (RuntimeException ignored) { }
        return ResponseEntity.ok().contentType(mt)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + (nf.nomeOriginal() != null ? nf.nomeOriginal().replaceAll("[^\\x20-\\x7E]", "_") : "nota-fiscal") + "\"")
                .body(nf.recurso());
    }

    @DeleteMapping("/abastecimentos/{id}")
    public ResponseEntity<Void> excluirAbastecimento(@PathVariable Long id) {
        frota.excluirAbastecimento(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/manutencoes/{id}")
    public ResponseEntity<Void> excluirManutencao(@PathVariable Long id) {
        frota.excluirManutencao(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/deslocamentos/{id}")
    public ResponseEntity<Void> excluirDeslocamento(@PathVariable Long id) {
        frota.excluirDeslocamento(id);
        return ResponseEntity.noContent().build();
    }

    // --- listagens ---

    @GetMapping("/abastecimentos")
    public Page<AbastecimentoResponse> abastecimentos(@ModelAttribute FiltroFrota filtro,
                                                      @PageableDefault(size = 25) Pageable pageable) {
        return frota.listarAbastecimentos(filtro, pageable);
    }

    @GetMapping("/manutencoes")
    public Page<ManutencaoVeiculoResponse> manutencoes(@ModelAttribute FiltroFrota filtro,
                                                       @PageableDefault(size = 25) Pageable pageable) {
        return frota.listarManutencoes(filtro, pageable);
    }

    @GetMapping("/deslocamentos")
    public Page<DeslocamentoResponse> deslocamentos(@ModelAttribute FiltroFrota filtro,
                                                    @PageableDefault(size = 25) Pageable pageable) {
        return frota.listarDeslocamentos(filtro, pageable);
    }

    @GetMapping("/catalogos")
    public Map<String, Object> catalogos() {
        return Map.of(
                "combustiveis", Arrays.stream(Combustivel.values()).map(c -> Map.of("valor", c.name(), "rotulo", c.getRotulo())).toList(),
                "servicos", Arrays.stream(TipoServicoVeiculo.values()).map(s -> Map.of("valor", s.name(), "rotulo", s.getRotulo())).toList(),
                "motivos", FrotaCatalog.MOTIVOS_DESLOCAMENTO,
                "lotacoes", FrotaCatalog.LOTACOES);
    }
}

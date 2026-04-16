package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.dto.request.BaixaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroPatrimonio;
import com.fundacao.gerenciador_patrimonial.dto.request.MovimentacaoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.PatrimonioResponse;
import com.fundacao.gerenciador_patrimonial.service.PatrimonioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Endpoints REST para Patrimônio.
 *
 * <p>A listagem aceita filtros via query params — o Spring MVC monta o
 * {@link FiltroPatrimonio} automaticamente a partir deles.</p>
 */
@RestController
@RequestMapping("/api/patrimonios")
@RequiredArgsConstructor
public class PatrimonioController {

    private final PatrimonioService service;

    @PostMapping
    public ResponseEntity<PatrimonioResponse> criar(@Valid @RequestBody PatrimonioRequest request) {
        PatrimonioResponse criado = service.criar(request);
        return ResponseEntity
                .created(URI.create("/api/patrimonios/" + criado.id()))
                .body(criado);
    }

    /**
     * Pesquisa paginada com filtros dinâmicos.
     * Exemplos:
     * <ul>
     *   <li>{@code GET /api/patrimonios?situacao=ATIVO&page=0&size=20}</li>
     *   <li>{@code GET /api/patrimonios?descricao=cadeira&upm=1 BPM}</li>
     * </ul>
     */
    @GetMapping
    public Page<PatrimonioResponse> pesquisar(
            @ModelAttribute FiltroPatrimonio filtro,
            Pageable pageable) {
        return service.pesquisar(filtro, pageable);
    }

    /** Atalho: apenas ativos (aba "Ativos" do sistema legado). */
    @GetMapping("/ativos")
    public Page<PatrimonioResponse> ativos(@ModelAttribute FiltroPatrimonio filtro, Pageable pageable) {
        FiltroPatrimonio comSituacao = new FiltroPatrimonio(
                filtro.descricao(), filtro.numeroTombo(), filtro.lotacaoId(),
                filtro.responsavelId(), filtro.upm(), filtro.categoria(),
                SituacaoPatrimonio.ATIVO, filtro.conservacao());
        return service.pesquisar(comSituacao, pageable);
    }

    /** Atalho: apenas baixados (aba "Baixados"). */
    @GetMapping("/baixados")
    public Page<PatrimonioResponse> baixados(@ModelAttribute FiltroPatrimonio filtro, Pageable pageable) {
        FiltroPatrimonio comSituacao = new FiltroPatrimonio(
                filtro.descricao(), filtro.numeroTombo(), filtro.lotacaoId(),
                filtro.responsavelId(), filtro.upm(), filtro.categoria(),
                SituacaoPatrimonio.BAIXADO, filtro.conservacao());
        return service.pesquisar(comSituacao, pageable);
    }

    @GetMapping("/{id}")
    public PatrimonioResponse buscar(@PathVariable Long id) {
        return service.buscarPorId(id);
    }

    @PutMapping("/{id}")
    public PatrimonioResponse atualizar(@PathVariable Long id,
                                        @Valid @RequestBody PatrimonioRequest request) {
        return service.atualizar(id, request);
    }

    @PostMapping("/{id}/movimentar")
    public PatrimonioResponse movimentar(@PathVariable Long id,
                                         @Valid @RequestBody MovimentacaoRequest request) {
        return service.movimentar(id, request);
    }

    @PostMapping("/{id}/baixa")
    public PatrimonioResponse baixar(@PathVariable Long id,
                                     @Valid @RequestBody BaixaRequest request) {
        return service.darBaixa(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirPermanentemente(@PathVariable Long id) {
        service.excluirPermanentemente(id);
    }
}

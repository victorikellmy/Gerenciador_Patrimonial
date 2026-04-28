package com.fundacao.gerenciador_patrimonial.service.dashboard;

import com.fundacao.gerenciador_patrimonial.domain.entity.Movimentacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.dto.response.AgrupamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.DashboardMetrics;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.MovimentacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService.CalculoDepreciacao;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consolida números e agrupamentos para o dashboard.
 *
 * <p>Todas as agregações são feitas por consultas SQL — não carregamos
 * entidades individuais de {@code Patrimonio} no caminho do dashboard.</p>
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter FMT_DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PatrimonioRepository patrimonioRepo;
    private final LotacaoRepository lotacaoRepo;
    private final ResponsavelRepository responsavelRepo;
    private final MovimentacaoRepository movimentacaoRepo;
    private final DepreciacaoService depreciacaoService;

    @Transactional(readOnly = true)
    public DashboardMetrics carregar() {

        // ----------------- contagens por situação -----------------
        // Inicializa todos os status em 0 para sempre aparecerem no gráfico.
        Map<String, Long> porSituacao = new LinkedHashMap<>();
        for (SituacaoPatrimonio s : SituacaoPatrimonio.values()) porSituacao.put(s.name(), 0L);
        for (Object[] row : patrimonioRepo.contarPorSituacao()) {
            SituacaoPatrimonio s = (SituacaoPatrimonio) row[0];
            porSituacao.put(s.name(), ((Number) row[1]).longValue());
        }
        long totalPatrimonios = porSituacao.values().stream().mapToLong(Long::longValue).sum();

        // ----------------- agrupamentos -----------------
        List<AgrupamentoResponse> porCategoria = patrimonioRepo.agruparPorCategoria().stream()
                .map(row -> AgrupamentoResponse.of(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        (BigDecimal) row[2]))
                .toList();

        List<AgrupamentoResponse> porConservacao = patrimonioRepo.agruparPorConservacao().stream()
                .map(row -> AgrupamentoResponse.of(
                        (String) row[0],
                        ((Number) row[1]).longValue()))
                .toList();

        List<AgrupamentoResponse> topUpms = patrimonioRepo
                .agruparPorUpm(PageRequest.of(0, 10)).stream()
                .map(row -> AgrupamentoResponse.of(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        (BigDecimal) row[2]))
                .toList();

        // ----------------- soma de valores + depreciação -----------------
        // Valor total ainda é uma agregação SQL (rápida).
        // Depreciação e VCL exigem cálculo time-based por item — feitos em Java
        // para manter consistência com DepreciacaoService (única fonte da fórmula).
        BigDecimal valorTotal   = patrimonioRepo.somarValorAtivos();
        BigDecimal depAcumTotal = BigDecimal.ZERO;
        BigDecimal vclTotal     = BigDecimal.ZERO;
        for (Patrimonio p : patrimonioRepo.findBySituacao(SituacaoPatrimonio.ATIVO)) {
            CalculoDepreciacao calc = depreciacaoService.calcular(p);
            depAcumTotal = depAcumTotal.add(calc.depreciacaoAcumulada());
            vclTotal     = vclTotal.add(calc.valorContabilLiquido());
        }

        // ----------------- últimas movimentações -----------------
        List<DashboardMetrics.MovimentacaoResumo> ultimas = movimentacaoRepo
                .listarUltimas(PageRequest.of(0, 10))
                .stream()
                .map(this::resumirMovimentacao)
                .toList();

        return new DashboardMetrics(
                totalPatrimonios,
                porSituacao,
                lotacaoRepo.count(),
                responsavelRepo.count(),
                valorTotal,
                depAcumTotal,
                vclTotal,
                porCategoria,
                porConservacao,
                topUpms,
                ultimas
        );
    }

    private DashboardMetrics.MovimentacaoResumo resumirMovimentacao(Movimentacao m) {
        return new DashboardMetrics.MovimentacaoResumo(
                m.getId(),
                m.getPatrimonio() != null ? m.getPatrimonio().getDescricao() : null,
                m.getPatrimonio() != null ? m.getPatrimonio().getNumeroTombo() : null,
                m.getLotacaoOrigem() != null ? m.getLotacaoOrigem().getNome() : null,
                m.getLotacaoDestino() != null ? m.getLotacaoDestino().getNome() : null,
                m.getResponsavelOrigem() != null ? m.getResponsavelOrigem().getNomeCompleto() : null,
                m.getResponsavelDestino() != null ? m.getResponsavelDestino().getNomeCompleto() : null,
                m.getDataMovimentacao() != null ? m.getDataMovimentacao().format(FMT_DATA_HORA) : null
        );
    }
}

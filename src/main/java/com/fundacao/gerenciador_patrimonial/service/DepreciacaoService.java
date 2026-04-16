package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.repository.PercentualConservacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.VidaUtilCategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula depreciação e métricas contábeis a partir do estado do patrimônio.
 *
 * <p>Essa lógica substitui as colunas I, K–Q da planilha original, que
 * eram derivadas por fórmulas.</p>
 */
@Service
@RequiredArgsConstructor
public class DepreciacaoService {

    private static final int SCALE = 2;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

    private final VidaUtilCategoriaRepository vutRepo;
    private final PercentualConservacaoRepository pcRepo;

    /**
     * Resultado dos cálculos. Todos os valores em R$ ou anos.
     */
    public record CalculoDepreciacao(
            Integer vutAnos,            // Vida útil total (anos)
            BigDecimal percentualVud,   // % decorrido (0.0–1.0)
            BigDecimal vudAnos,         // Anos decorridos
            BigDecimal vurAnos,         // Anos remanescentes
            BigDecimal depreciacaoAcumulada,
            BigDecimal valorContabilLiquido,
            BigDecimal depreciacaoAnual
    ) {
        public static CalculoDepreciacao vazio() {
            return new CalculoDepreciacao(null, null, null, null, ZERO, ZERO, ZERO);
        }
    }

    /**
     * Calcula todas as métricas de um patrimônio.
     * Se faltar categoria, conservação ou valor de compra, devolve estrutura zerada
     * (não é erro — apenas não há dados suficientes).
     */
    public CalculoDepreciacao calcular(Patrimonio p) {
        if (p.getCategoria() == null || p.getConservacao() == null || p.getValorCompra() == null) {
            return CalculoDepreciacao.vazio();
        }

        Integer vut = vutRepo.findByCategoriaIgnoreCase(p.getCategoria())
                .map(v -> v.getVutAnos())
                .orElse(null);

        BigDecimal vudPct = pcRepo.findByConservacao(p.getConservacao())
                .map(c -> c.getPercentualVud())
                .orElse(null);

        if (vut == null || vudPct == null) {
            return CalculoDepreciacao.vazio();
        }

        BigDecimal valor = p.getValorCompra();
        BigDecimal vutBd = BigDecimal.valueOf(vut);

        BigDecimal vudAnos  = vutBd.multiply(vudPct).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal vurAnos  = vutBd.subtract(vudAnos).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal deprAcum = valor.multiply(vudPct).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal vcl      = valor.subtract(deprAcum).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal deprAno  = valor.divide(vutBd, SCALE, RoundingMode.HALF_UP);

        return new CalculoDepreciacao(vut, vudPct, vudAnos, vurAnos, deprAcum, vcl, deprAno);
    }
}

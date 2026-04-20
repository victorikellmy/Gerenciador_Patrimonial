package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.repository.PercentualConservacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.VidaUtilCategoriaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    // Tabelas de referência populadas por Flyway (V2__seed_reference_data) e imutáveis em runtime.
    // Carregadas uma vez no startup para evitar N+1 ao iterar patrimônios (dashboard, relatórios).
    private final Map<String, Integer> vutPorCategoria = new ConcurrentHashMap<>();
    private final Map<Conservacao, BigDecimal> vudPorConservacao = new ConcurrentHashMap<>();

    @PostConstruct
    void preloadReferencias() {
        vutRepo.findAll().forEach(v ->
                vutPorCategoria.put(v.getCategoria().toUpperCase(Locale.ROOT), v.getVutAnos()));
        vudPorConservacao.putAll(pcRepo.findAll().stream()
                .collect(Collectors.toMap(c -> c.getConservacao(), c -> c.getPercentualVud(),
                        (a, b) -> a)));
    }

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

        Integer vut = vutPorCategoria.get(p.getCategoria().toUpperCase(Locale.ROOT));
        BigDecimal vudPct = vudPorConservacao.get(p.getConservacao());

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

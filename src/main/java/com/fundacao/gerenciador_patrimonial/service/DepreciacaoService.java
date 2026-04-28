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
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Calcula depreciação e métricas contábeis para um {@link Patrimonio}.
 *
 * <h2>Estratégia condicional</h2>
 * O método de cálculo é decidido pelo dado disponível, não por configuração:
 * <ul>
 *   <li><b>TEMPO</b> (preferida) — quando {@code dataCompra} está preenchida.
 *       Modelo linear pelo tempo decorrido desde a aquisição. Os valores
 *       avançam dia a dia automaticamente.</li>
 *   <li><b>CONSERVAÇÃO</b> (legado) — quando {@code dataCompra} é nula.
 *       Mantém o cálculo histórico baseado no % de VUD por estado de
 *       conservação (tabela {@code percentual_conservacao}). Garante que
 *       registros antigos importados da planilha permaneçam com as mesmas
 *       métricas que tinham antes desta refatoração.</li>
 * </ul>
 *
 * <p>O DTO de saída ({@link CalculoDepreciacao}) tem a mesma forma para
 * ambas as estratégias — o front-end e demais consumidores não precisam
 * saber qual motor foi usado, apenas inspecionar
 * {@link CalculoDepreciacao#calculoLegado()} se quiserem destacar o registro.</p>
 */
@Service
@RequiredArgsConstructor
public class DepreciacaoService {

    private static final int SCALE_VALOR = 2;
    private static final int SCALE_ANOS  = 2;
    private static final int SCALE_PCT   = 4;
    private static final BigDecimal DIAS_POR_ANO = new BigDecimal("365.25");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE_VALOR, RoundingMode.HALF_UP);

    private final VidaUtilCategoriaRepository vutRepo;
    private final PercentualConservacaoRepository pcRepo;
    private final Clock clock = Clock.systemDefaultZone();

    // Tabelas de referência populadas por Flyway e imutáveis em runtime.
    // Carregadas uma vez no startup para evitar N+1 ao iterar patrimônios.
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

    public record CalculoDepreciacao(
            Integer vutAnos,
            BigDecimal percentualVud,         // 0.0000–1.0000 (fração da VUT consumida)
            BigDecimal vudAnos,
            BigDecimal vurAnos,
            BigDecimal depreciacaoAcumulada,
            BigDecimal valorContabilLiquido,
            BigDecimal depreciacaoAnual,
            LocalDate  dataReferencia,        // base usada no cálculo TEMPO; null no legado
            boolean    calculoLegado          // true ⇒ usou % por conservação (sem dataCompra)
    ) {
        public static CalculoDepreciacao vazio() {
            return new CalculoDepreciacao(null, null, null, null, ZERO, ZERO, ZERO, null, false);
        }
    }

    /** Cálculo usando "hoje" como data de referência. */
    public CalculoDepreciacao calcular(Patrimonio p) {
        return calcular(p, LocalDate.now(clock));
    }

    /**
     * Variante testável (data de referência injetável).
     * Despacha entre as duas estratégias com base na presença de {@code dataCompra}.
     */
    public CalculoDepreciacao calcular(Patrimonio p, LocalDate hoje) {
        if (p == null || p.getCategoria() == null || p.getValorCompra() == null) {
            return CalculoDepreciacao.vazio();
        }
        Integer vut = vutPorCategoria.get(p.getCategoria().toUpperCase(Locale.ROOT));
        if (vut == null || vut <= 0) {
            return CalculoDepreciacao.vazio();
        }

        // Dispatch condicional: presença da data define a estratégia.
        if (p.getDataCompra() != null) {
            return calcularPorTempo(p, hoje, vut);
        }
        return calcularPorConservacao(p, vut);
    }

    // =========================================================================
    // Estratégia A — TEMPO (cadastros novos com dataCompra)
    // =========================================================================

    private CalculoDepreciacao calcularPorTempo(Patrimonio p, LocalDate hoje, int vut) {
        BigDecimal valor = p.getValorCompra();
        BigDecimal vutBd = BigDecimal.valueOf(vut);

        long dias = ChronoUnit.DAYS.between(p.getDataCompra(), hoje);
        if (dias < 0) dias = 0;                                  // data futura ⇒ ainda não depreciou

        BigDecimal vudAnos = BigDecimal.valueOf(dias)
                .divide(DIAS_POR_ANO, SCALE_ANOS, RoundingMode.HALF_UP);
        if (vudAnos.compareTo(vutBd) > 0) vudAnos = vutBd;       // cap: não passa da VUT

        BigDecimal vurAnos = vutBd.subtract(vudAnos)
                .max(BigDecimal.ZERO)
                .setScale(SCALE_ANOS, RoundingMode.HALF_UP);
        BigDecimal pctVud  = vudAnos.divide(vutBd, SCALE_PCT, RoundingMode.HALF_UP);

        BigDecimal deprAno  = valor.divide(vutBd, SCALE_VALOR, RoundingMode.HALF_UP);
        BigDecimal deprAcum = deprAno.multiply(vudAnos).setScale(SCALE_VALOR, RoundingMode.HALF_UP);
        if (deprAcum.compareTo(valor) > 0) deprAcum = valor;     // cap: não deprecia além do custo
        BigDecimal vcl = valor.subtract(deprAcum).setScale(SCALE_VALOR, RoundingMode.HALF_UP);

        return new CalculoDepreciacao(
                vut, pctVud, vudAnos, vurAnos, deprAcum, vcl, deprAno,
                p.getDataCompra(), false);
    }

    // =========================================================================
    // Estratégia B — CONSERVAÇÃO (legado, sem dataCompra)
    // Replica a fórmula original que existia antes da mudança time-based.
    // =========================================================================

    private CalculoDepreciacao calcularPorConservacao(Patrimonio p, int vut) {
        BigDecimal pctVud = p.getConservacao() != null
                ? vudPorConservacao.get(p.getConservacao())
                : null;
        if (pctVud == null) {
            return CalculoDepreciacao.vazio();
        }

        BigDecimal valor = p.getValorCompra();
        BigDecimal vutBd = BigDecimal.valueOf(vut);

        BigDecimal vudAnos  = vutBd.multiply(pctVud).setScale(SCALE_ANOS, RoundingMode.HALF_UP);
        BigDecimal vurAnos  = vutBd.subtract(vudAnos).setScale(SCALE_ANOS, RoundingMode.HALF_UP);
        BigDecimal deprAcum = valor.multiply(pctVud).setScale(SCALE_VALOR, RoundingMode.HALF_UP);
        BigDecimal vcl      = valor.subtract(deprAcum).setScale(SCALE_VALOR, RoundingMode.HALF_UP);
        BigDecimal deprAno  = valor.divide(vutBd, SCALE_VALOR, RoundingMode.HALF_UP);

        return new CalculoDepreciacao(
                vut, pctVud, vudAnos, vurAnos, deprAcum, vcl, deprAno,
                null, true);
    }
}

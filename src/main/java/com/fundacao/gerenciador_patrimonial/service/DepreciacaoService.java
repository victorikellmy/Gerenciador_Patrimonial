package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.projection.PatrimonioDepreciavel;
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
            BigDecimal valorRecuperavel,      // copiado do patrimônio (insumo do laudo)
            BigDecimal perdaImpairment,       // max(0, VCL − valorRecuperavel) quando há laudo
            BigDecimal depreciacaoAnual,
            LocalDate  dataReferencia,        // base usada no cálculo TEMPO; null no legado
            boolean    calculoLegado          // true ⇒ usou % por conservação (sem dataCompra)
    ) {
        public static CalculoDepreciacao vazio() {
            return new CalculoDepreciacao(null, null, null, null, ZERO, ZERO, null, null, ZERO, null, false);
        }
    }

    private static BigDecimal calcularPerdaImpairment(BigDecimal vcl, BigDecimal valorRecuperavel) {
        if (valorRecuperavel == null || vcl == null) return null;
        BigDecimal perda = vcl.subtract(valorRecuperavel);
        return perda.signum() > 0 ? perda.setScale(SCALE_VALOR, RoundingMode.HALF_UP) : ZERO;
    }

    /** Adapter para a entidade — usado nos templates Thymeleaf e nos exporters. */
    public CalculoDepreciacao calcular(Patrimonio p) {
        return p == null ? CalculoDepreciacao.vazio() : calcular(projecaoDe(p));
    }

    /** Cálculo usando "hoje" como data de referência. Caminho principal. */
    public CalculoDepreciacao calcular(PatrimonioDepreciavel p) {
        return calcular(p, LocalDate.now(clock));
    }

    /**
     * Variante testável (data de referência injetável).
     * Despacha entre as duas estratégias com base na presença de {@code dataCompra}.
     */
    public CalculoDepreciacao calcular(PatrimonioDepreciavel p, LocalDate hoje) {
        if (p == null || p.categoria() == null || p.valorCompra() == null) {
            return CalculoDepreciacao.vazio();
        }
        Integer vut = vutPorCategoria.get(p.categoria().toUpperCase(Locale.ROOT));
        if (vut == null || vut <= 0) {
            return CalculoDepreciacao.vazio();
        }

        // Dispatch condicional: presença da data define a estratégia.
        return p.dataCompra() != null
                ? calcularPorTempo(p, hoje, vut)
                : calcularPorConservacao(p, vut);
    }

    private static PatrimonioDepreciavel projecaoDe(Patrimonio p) {
        return new PatrimonioDepreciavel(
                p.getCategoria(), p.getValorCompra(), p.getDataCompra(),
                p.getConservacao(), p.getValorRecuperavel());
    }

    // =========================================================================
    // Estratégia A — TEMPO (cadastros novos com dataCompra)
    // =========================================================================

    private CalculoDepreciacao calcularPorTempo(PatrimonioDepreciavel p, LocalDate hoje, int vut) {
        BigDecimal valor = p.valorCompra();
        BigDecimal vutBd = BigDecimal.valueOf(vut);

        long dias = Math.max(0, ChronoUnit.DAYS.between(p.dataCompra(), hoje));  // futuro ⇒ não depreciou

        BigDecimal vudAnos = BigDecimal.valueOf(dias)
                .divide(DIAS_POR_ANO, SCALE_ANOS, RoundingMode.HALF_UP)
                .min(vutBd);                                          // cap: não passa da VUT

        BigDecimal vurAnos = vutBd.subtract(vudAnos)
                .max(BigDecimal.ZERO)
                .setScale(SCALE_ANOS, RoundingMode.HALF_UP);
        BigDecimal pctVud  = vudAnos.divide(vutBd, SCALE_PCT, RoundingMode.HALF_UP);

        BigDecimal deprAno  = valor.divide(vutBd, SCALE_VALOR, RoundingMode.HALF_UP);
        BigDecimal deprAcum = deprAno.multiply(vudAnos)
                .setScale(SCALE_VALOR, RoundingMode.HALF_UP)
                .min(valor);                                           // cap: não deprecia além do custo
        BigDecimal vcl = valor.subtract(deprAcum).setScale(SCALE_VALOR, RoundingMode.HALF_UP);

        return new CalculoDepreciacao(
                vut, pctVud, vudAnos, vurAnos, deprAcum, vcl,
                p.valorRecuperavel(), calcularPerdaImpairment(vcl, p.valorRecuperavel()),
                deprAno, p.dataCompra(), false);
    }

    // =========================================================================
    // Estratégia B — CONSERVAÇÃO (legado, sem dataCompra)
    // Replica a fórmula original que existia antes da mudança time-based.
    // =========================================================================

    private CalculoDepreciacao calcularPorConservacao(PatrimonioDepreciavel p, int vut) {
        BigDecimal pctVud = p.conservacao() != null
                ? vudPorConservacao.get(p.conservacao())
                : null;
        if (pctVud == null) {
            return CalculoDepreciacao.vazio();
        }

        BigDecimal valor = p.valorCompra();
        BigDecimal vutBd = BigDecimal.valueOf(vut);

        BigDecimal vudAnos  = vutBd.multiply(pctVud).setScale(SCALE_ANOS, RoundingMode.HALF_UP);
        BigDecimal vurAnos  = vutBd.subtract(vudAnos).setScale(SCALE_ANOS, RoundingMode.HALF_UP);
        BigDecimal deprAcum = valor.multiply(pctVud).setScale(SCALE_VALOR, RoundingMode.HALF_UP);
        BigDecimal vcl      = valor.subtract(deprAcum).setScale(SCALE_VALOR, RoundingMode.HALF_UP);
        BigDecimal deprAno  = valor.divide(vutBd, SCALE_VALOR, RoundingMode.HALF_UP);

        return new CalculoDepreciacao(
                vut, pctVud, vudAnos, vurAnos, deprAcum, vcl,
                p.valorRecuperavel(), calcularPerdaImpairment(vcl, p.valorRecuperavel()),
                deprAno, null, true);
    }
}

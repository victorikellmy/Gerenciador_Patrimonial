package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.PercentualConservacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.VidaUtilCategoria;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.projection.PatrimonioDepreciavel;
import com.fundacao.gerenciador_patrimonial.repository.PercentualConservacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.VidaUtilCategoriaRepository;
import com.fundacao.gerenciador_patrimonial.service.DepreciacaoService.CalculoDepreciacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testes das duas estratégias de depreciação:
 * TEMPO (com dataCompra) e CONSERVAÇÃO (legado, sem dataCompra).
 */
class DepreciacaoServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 8, 1);

    private DepreciacaoService service;

    @BeforeEach
    void setUp() {
        var vutRepo = mock(VidaUtilCategoriaRepository.class);
        var pcRepo  = mock(PercentualConservacaoRepository.class);

        when(vutRepo.findAll()).thenReturn(List.of(
                VidaUtilCategoria.builder().categoria("COMPUTADOR").vutAnos(5).build(),
                VidaUtilCategoria.builder().categoria("MOVEIS").vutAnos(10).build()));
        when(pcRepo.findAll()).thenReturn(List.of(
                PercentualConservacao.builder()
                        .conservacao(Conservacao.BOM)
                        .percentualVud(new BigDecimal("0.4000"))
                        .build()));

        service = new DepreciacaoService(vutRepo, pcRepo);
        service.preloadReferencias();
    }

    private static PatrimonioDepreciavel item(String categoria, String valor,
                                              LocalDate dataCompra, Conservacao cons,
                                              String valorRecuperavel) {
        return new PatrimonioDepreciavel(
                categoria,
                valor != null ? new BigDecimal(valor) : null,
                dataCompra,
                cons,
                valorRecuperavel != null ? new BigDecimal(valorRecuperavel) : null);
    }

    // =========================================================================
    // Estratégia TEMPO
    // =========================================================================

    @Test
    @DisplayName("TEMPO: 730 dias de uso com VUT 5 deprecia 2/5 do valor")
    void tempoDepreciacaoLinear() {
        var calc = service.calcular(item("COMPUTADOR", "1000.00", HOJE.minusDays(730),
                null, null), HOJE);

        assertThat(calc.calculoLegado()).isFalse();
        assertThat(calc.vutAnos()).isEqualTo(5);
        assertThat(calc.vudAnos()).isEqualByComparingTo("2.00");
        assertThat(calc.vurAnos()).isEqualByComparingTo("3.00");
        assertThat(calc.percentualVud()).isEqualByComparingTo("0.4000");
        assertThat(calc.depreciacaoAnual()).isEqualByComparingTo("200.00");
        assertThat(calc.depreciacaoAcumulada()).isEqualByComparingTo("400.00");
        assertThat(calc.valorContabilLiquido()).isEqualByComparingTo("600.00");
        assertThat(calc.dataReferencia()).isEqualTo(HOJE.minusDays(730));
    }

    @Test
    @DisplayName("TEMPO: uso além da VUT não deprecia além do custo (caps)")
    void tempoCapNaVutENoValor() {
        var calc = service.calcular(item("COMPUTADOR", "1000.00", HOJE.minusYears(20),
                null, null), HOJE);

        assertThat(calc.vudAnos()).isEqualByComparingTo("5.00");   // cap na VUT
        assertThat(calc.vurAnos()).isEqualByComparingTo("0.00");
        assertThat(calc.depreciacaoAcumulada()).isEqualByComparingTo("1000.00"); // cap no custo
        assertThat(calc.valorContabilLiquido()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("TEMPO: data de compra futura não gera depreciação")
    void tempoDataFutura() {
        var calc = service.calcular(item("COMPUTADOR", "1000.00", HOJE.plusDays(30),
                null, null), HOJE);

        assertThat(calc.depreciacaoAcumulada()).isEqualByComparingTo("0.00");
        assertThat(calc.valorContabilLiquido()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("TEMPO: impairment = max(0, VCL - valorRecuperavel)")
    void impairment() {
        // VCL = 600.00 (mesmo cenário do teste linear)
        var comPerda = service.calcular(item("COMPUTADOR", "1000.00", HOJE.minusDays(730),
                null, "500.00"), HOJE);
        assertThat(comPerda.perdaImpairment()).isEqualByComparingTo("100.00");

        var semPerda = service.calcular(item("COMPUTADOR", "1000.00", HOJE.minusDays(730),
                null, "700.00"), HOJE);
        assertThat(semPerda.perdaImpairment()).isEqualByComparingTo("0.00");
    }

    // =========================================================================
    // Estratégia CONSERVAÇÃO (legado)
    // =========================================================================

    @Test
    @DisplayName("LEGADO: sem dataCompra usa percentual VUD por conservação")
    void legadoPorConservacao() {
        var calc = service.calcular(item("MOVEIS", "1000.00", null,
                Conservacao.BOM, null), HOJE);

        assertThat(calc.calculoLegado()).isTrue();
        assertThat(calc.dataReferencia()).isNull();
        assertThat(calc.vutAnos()).isEqualTo(10);
        assertThat(calc.vudAnos()).isEqualByComparingTo("4.00");           // 10 * 0.40
        assertThat(calc.depreciacaoAcumulada()).isEqualByComparingTo("400.00");
        assertThat(calc.valorContabilLiquido()).isEqualByComparingTo("600.00");
        assertThat(calc.depreciacaoAnual()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("LEGADO: sem conservação cadastrada retorna cálculo vazio")
    void legadoSemConservacao() {
        var calc = service.calcular(item("MOVEIS", "1000.00", null, null, null), HOJE);
        assertThat(calc.vutAnos()).isNull();
        assertThat(calc.depreciacaoAcumulada()).isEqualByComparingTo("0.00");
    }

    // =========================================================================
    // Casos vazios e mapa de VUT
    // =========================================================================

    @Test
    @DisplayName("Categoria desconhecida, sem valor ou sem categoria → cálculo vazio")
    void casosVazios() {
        assertThat(service.calcular(item("INEXISTENTE", "1000.00", HOJE.minusDays(10), null, null), HOJE).vutAnos()).isNull();
        assertThat(service.calcular(item("COMPUTADOR", null, HOJE.minusDays(10), null, null), HOJE).vutAnos()).isNull();
        assertThat(service.calcular(item(null, "1000.00", HOJE.minusDays(10), null, null), HOJE).vutAnos()).isNull();
        assertThat(service.calcular((PatrimonioDepreciavel) null, HOJE).vutAnos()).isNull();
    }

    @Test
    @DisplayName("vutDaCategoria é case-insensitive e null-safe")
    void vutDaCategoria() {
        assertThat(service.vutDaCategoria("computador")).isEqualTo(5);
        assertThat(service.vutDaCategoria("MOVEIS")).isEqualTo(10);
        assertThat(service.vutDaCategoria("INEXISTENTE")).isNull();
        assertThat(service.vutDaCategoria(null)).isNull();
    }
}

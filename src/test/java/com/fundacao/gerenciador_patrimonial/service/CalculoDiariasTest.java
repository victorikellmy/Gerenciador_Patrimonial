package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras de apuração do Requerimento de Diárias (mesmos casos do formulário HTML de referência). */
class CalculoDiariasTest {

    private static LocalDateTime t(String iso) { return LocalDateTime.parse(iso); }

    private static String qtd(String saida, String retorno) {
        return CalculoDiarias.calcular(t(saida), t(retorno)).quantidade().toPlainString();
    }

    @Test
    void periodoCompletoDe24hVale1Diaria() {
        var r = CalculoDiarias.calcular(t("2026-11-23T08:00"), t("2026-11-24T08:00"));
        assertThat(r.integrais()).isEqualTo(1);
        assertThat(r.restoMin()).isZero();
        assertThat(r.refeicoes()).isEmpty();
        assertThat(r.quantidade()).isEqualByComparingTo("1.0");
    }

    @Test
    void restoComAlmocoOuJantarAcrescentaMeiaLimitadaAMeia() {
        // 2 períodos completos + resto das 08:00 às 19:00 (pega 12h e 18h): só 0,5 a mais
        var r = CalculoDiarias.calcular(t("2026-11-23T08:00"), t("2026-11-25T19:00"));
        assertThat(r.integrais()).isEqualTo(2);
        assertThat(r.refeicoes()).containsExactly(t("2026-11-25T12:00"), t("2026-11-25T18:00"));
        assertThat(r.quantidade()).isEqualByComparingTo("2.5");
        assertThat(r.inicioResto()).isEqualTo(t("2026-11-25T08:00"));
    }

    @Test
    void afastamentoInferiorA24hSegueARegraDoResto() {
        assertThat(qtd("2026-11-23T07:00", "2026-11-23T13:00")).isEqualTo("0.5");   // pega o almoço
        assertThat(qtd("2026-11-23T13:00", "2026-11-23T17:59")).isEqualTo("0.0");   // nem 12h nem 18h
        assertThat(qtd("2026-11-23T20:00", "2026-11-24T10:00")).isEqualTo("0.0");   // noite, sem refeição
        assertThat(qtd("2026-11-23T20:00", "2026-11-24T12:00")).isEqualTo("0.5");   // almoço do dia seguinte
    }

    @Test
    void horariosExatosDe12hE18hContam() {
        assertThat(qtd("2026-11-23T12:00", "2026-11-23T12:30")).isEqualTo("0.5");   // começa às 12h em ponto
        assertThat(qtd("2026-11-23T17:00", "2026-11-23T18:00")).isEqualTo("0.5");   // termina às 18h em ponto
        assertThat(qtd("2026-11-23T12:01", "2026-11-23T17:59")).isEqualTo("0.0");
    }

    @Test
    void restoQueViraODiaConsideraAsRefeicoesDoDiaSeguinte() {
        // 1 período completo (até 24/11 20:00) + resto até 25/11 12:00
        var r = CalculoDiarias.calcular(t("2026-11-23T20:00"), t("2026-11-25T12:00"));
        assertThat(r.integrais()).isEqualTo(1);
        assertThat(r.refeicoes()).containsExactly(t("2026-11-25T12:00"));
        assertThat(r.quantidade()).isEqualByComparingTo("1.5");
    }

    @Test
    void retornoTemDeSerPosteriorASaida() {
        assertThatThrownBy(() -> CalculoDiarias.calcular(t("2026-11-23T08:00"), t("2026-11-23T08:00")))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("posterior");
        assertThatThrownBy(() -> CalculoDiarias.calcular(t("2026-11-23T08:00"), t("2026-11-22T18:00")))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("anterior");
    }

    @Test
    void avisaSobreSextaEFimDeSemanaSemBloquear() {
        // 23/11/2026 é segunda-feira
        assertThat(CalculoDiarias.calcular(t("2026-11-23T08:00"), t("2026-11-25T18:00")).avisoPortaria()).isNull();
        assertThat(CalculoDiarias.calcular(t("2026-11-27T08:00"), t("2026-11-27T18:00")).avisoPortaria())
                .contains("sexta-feira");
        assertThat(CalculoDiarias.calcular(t("2026-11-26T08:00"), t("2026-11-28T18:00")).avisoPortaria())
                .contains("sábado ou domingo");
    }
}

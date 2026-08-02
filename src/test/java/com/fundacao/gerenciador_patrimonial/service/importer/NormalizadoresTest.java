package com.fundacao.gerenciador_patrimonial.service.importer;

import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes das funções puras de limpeza dos dados da planilha. */
class NormalizadoresTest {

    @Test
    @DisplayName("normalizarUpm: remove ordinais, uniformiza espaços e separa número da sigla")
    void normalizarUpm() {
        assertThat(Normalizadores.normalizarUpm("1BPM")).isEqualTo("1 BPM");
        assertThat(Normalizadores.normalizarUpm("2º BPM  ARAGUAINA")).isEqualTo("2 BPM ARAGUAINA");
        assertThat(Normalizadores.normalizarUpm("  3°CIPM ")).isEqualTo("3 CIPM");
        assertThat(Normalizadores.normalizarUpm("sede")).isEqualTo("SEDE");
        assertThat(Normalizadores.normalizarUpm("   ")).isNull();
        assertThat(Normalizadores.normalizarUpm(null)).isNull();
    }

    @Test
    @DisplayName("normalizarSala: conserta célula corrompida por fórmula e typo conhecido")
    void normalizarSala() {
        assertThat(Normalizadores.normalizarSala("CON+B85:D92S. ODONTO 2")).isEqualTo("CONS. ODONTO 2");
        assertThat(Normalizadores.normalizarSala("amox")).isEqualTo("ALMOX");
        assertThat(Normalizadores.normalizarSala("  sala   reunião ")).isEqualTo("SALA REUNIÃO");
        assertThat(Normalizadores.normalizarSala(null)).isNull();
    }

    @Test
    @DisplayName("normalizarNome: colapsa espaços preservando o conteúdo")
    void normalizarNome() {
        assertThat(Normalizadores.normalizarNome("  João   da  Silva ")).isEqualTo("João da Silva");
        assertThat(Normalizadores.normalizarNome("")).isNull();
        assertThat(Normalizadores.normalizarNome(null)).isNull();
    }

    @Test
    @DisplayName("normalizarTombo: placeholders (0, 00, -, vazio) viram null")
    void normalizarTombo() {
        assertThat(Normalizadores.normalizarTombo(" 12 345 ")).isEqualTo("12345");
        assertThat(Normalizadores.normalizarTombo("0")).isNull();
        assertThat(Normalizadores.normalizarTombo("000")).isNull();
        assertThat(Normalizadores.normalizarTombo("-")).isNull();
        assertThat(Normalizadores.normalizarTombo("  ")).isNull();
        assertThat(Normalizadores.normalizarTombo(null)).isNull();
        assertThat(Normalizadores.normalizarTombo("102030")).isEqualTo("102030");
    }

    @Test
    @DisplayName("Conservacao.fromPlanilha: aceita variações e rejeita situações administrativas")
    void conservacaoFromPlanilha() {
        assertThat(Conservacao.fromPlanilha("ÓTIMO")).isEqualTo(Conservacao.OTIMO);
        assertThat(Conservacao.fromPlanilha("bom/regular")).isEqualTo(Conservacao.BOM_REGULAR);
        assertThat(Conservacao.fromPlanilha("BOM / REGULAR")).isEqualTo(Conservacao.BOM_REGULAR);
        assertThat(Conservacao.fromPlanilha("regular / ruim")).isEqualTo(Conservacao.REGULAR_RUIM);
        assertThat(Conservacao.fromPlanilha("CAUTELADO")).isNull();
        assertThat(Conservacao.fromPlanilha("TECNICO")).isNull();
        assertThat(Conservacao.fromPlanilha("  ")).isNull();
        assertThat(Conservacao.fromPlanilha(null)).isNull();
    }
}

package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Apuração da quantidade de diárias de um afastamento, conforme o Requerimento de Diárias da FPTO:
 * <ul>
 *   <li>cada período completo de 24 horas vale 1 diária (50% alimentação + 50% hospedagem);</li>
 *   <li>no intervalo restante (ou no afastamento inferior a 24 horas), a presença do horário de
 *       almoço (12h) ou de jantar (18h) gera 0,5 diária de alimentação — limitada a 0,5 mesmo que
 *       o intervalo abranja os dois horários;</li>
 *   <li>12h e 18h exatos contam (limites incluídos); intervalo sem duração não gera parcela.</li>
 * </ul>
 * Datas e horários são os do fuso da origem, sem conversão.
 */
public final class CalculoDiarias {

    private static final long MINUTOS_DIA = 1440;
    private static final int[] HORAS_REFEICAO = {12, 18};
    private static final BigDecimal MEIA = new BigDecimal("0.5");

    private CalculoDiarias() {}

    /**
     * @param integrais    períodos completos de 24 horas
     * @param alimentacao  0,5 quando o intervalo restante abrange 12h ou 18h; senão 0
     * @param quantidade   integrais + alimentação
     * @param duracaoMin   duração total do afastamento, em minutos
     * @param restoMin     minutos que sobram depois dos períodos completos
     * @param inicioResto  instante em que o intervalo restante começa
     * @param refeicoes    horários de 12h/18h que caem no intervalo restante
     * @param avisoPortaria alerta do art. 7º da Portaria 001/2019 (não bloqueia), ou null
     */
    public record Resultado(int integrais, BigDecimal alimentacao, BigDecimal quantidade, long duracaoMin,
                            long restoMin, LocalDateTime inicioResto, List<LocalDateTime> refeicoes,
                            String avisoPortaria) {}

    public static Resultado calcular(LocalDateTime saida, LocalDateTime retorno) {
        if (saida == null || retorno == null) {
            throw new RegraDeNegocioException("Informe as datas e os horários de saída e de retorno.");
        }
        if (retorno.toLocalDate().isBefore(saida.toLocalDate())) {
            throw new RegraDeNegocioException("A data de retorno à origem não pode ser anterior à data de saída.");
        }
        if (!retorno.isAfter(saida)) {
            throw new RegraDeNegocioException("O retorno deve ser posterior à saída.");
        }
        long duracao = Duration.between(saida, retorno).toMinutes();
        int integrais = (int) (duracao / MINUTOS_DIA);
        long resto = duracao % MINUTOS_DIA;
        LocalDateTime inicioResto = saida.plusDays(integrais);

        List<LocalDateTime> refeicoes = new ArrayList<>();
        if (resto > 0) {
            for (LocalDate dia = inicioResto.toLocalDate(); !dia.isAfter(retorno.toLocalDate()); dia = dia.plusDays(1)) {
                for (int hora : HORAS_REFEICAO) {
                    LocalDateTime t = dia.atTime(hora, 0);
                    if (!t.isBefore(inicioResto) && !t.isAfter(retorno)) refeicoes.add(t);
                }
            }
        }
        BigDecimal alimentacao = refeicoes.isEmpty() ? BigDecimal.ZERO : MEIA;
        BigDecimal quantidade = BigDecimal.valueOf(integrais).add(alimentacao).setScale(1);
        return new Resultado(integrais, alimentacao, quantidade, duracao, resto, inicioResto,
                List.copyOf(refeicoes), avisoArt7(saida.toLocalDate(), retorno.toLocalDate()));
    }

    /**
     * Art. 7º: é nulo o ato de atribuição de diária que comece na sexta-feira ou inclua sábado,
     * domingo ou feriado, salvo se amplamente motivado. Só avisa (feriados não são conhecidos aqui).
     */
    static String avisoArt7(LocalDate saida, LocalDate retorno) {
        boolean sexta = saida.getDayOfWeek() == DayOfWeek.FRIDAY;
        boolean fimDeSemana = false;
        for (LocalDate d = saida; !d.isAfter(retorno) && !fimDeSemana; d = d.plusDays(1)) {
            fimDeSemana = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
        }
        if (!sexta && !fimDeSemana) return null;
        return "Atenção ao art. 7º da Portaria 001/2019: o período "
                + (sexta && fimDeSemana ? "começa na sexta-feira e inclui sábado ou domingo"
                    : sexta ? "começa na sexta-feira" : "inclui sábado ou domingo")
                + ". A diária só é válida se a finalidade estiver amplamente motivada.";
    }
}

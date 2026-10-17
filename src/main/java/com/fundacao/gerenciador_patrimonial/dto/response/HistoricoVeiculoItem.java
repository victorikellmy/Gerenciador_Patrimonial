package com.fundacao.gerenciador_patrimonial.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Uma linha do histórico unificado do veículo (abastecimento, manutenção ou
 * deslocamento), ordenado por data.
 */
public record HistoricoVeiculoItem(
        Tipo tipo,
        Long id,
        LocalDate data,
        LocalTime hora,
        String titulo,
        String detalhe,
        Integer km,
        BigDecimal valor,
        String motorista,
        boolean emAndamento,
        boolean possuiNotaFiscal
) {
    public enum Tipo { ABASTECIMENTO, MANUTENCAO, DESLOCAMENTO }
}

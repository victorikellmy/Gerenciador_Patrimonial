package com.fundacao.gerenciador_patrimonial.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Painel da frota: números do mês corrente e veículos na rua. */
public record FrotaResumo(
        long veiculosAtivos,
        long naRua,
        long deslocamentosMes,
        BigDecimal combustivelMes,
        BigDecimal manutencaoMes,
        long manutencoesMes,
        List<DeslocamentoResponse> emAndamento
) {}

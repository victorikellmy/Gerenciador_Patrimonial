package com.fundacao.gerenciador_patrimonial.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Números do veículo para a ficha (ano corrente, salvo indicação). */
public record VeiculoResumo(
        Integer kmAtual,
        BigDecimal combustivelAno,
        BigDecimal litrosAno,
        BigDecimal manutencaoAno,
        long kmRodadosAno,
        BigDecimal consumoMedioKmPorLitro,
        long qtdAbastecimentos,
        long qtdManutencoes,
        long qtdDeslocamentos,
        DeslocamentoResponse deslocamentoAberto,
        Integer proximaRevisaoKm,
        LocalDate proximaRevisaoData,
        boolean revisaoVencida
) {}

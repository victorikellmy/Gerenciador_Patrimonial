package com.fundacao.gerenciador_patrimonial.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Painel gerencial da frota (perfil GERENTE e gestores): onde está cada viatura,
 * quem está com ela, o que está em manutenção, revisões vencidas e os gastos.
 */
public record GestaoFrotaResponse(
        LocalDate hoje,
        // --- contagens ---
        long veiculosAtivos,
        long naRua,
        long emManutencao,
        long disponiveis,
        long revisoesVencidas,
        // --- gastos ---
        BigDecimal combustivelMes,
        BigDecimal manutencaoMes,
        BigDecimal combustivelAno,
        BigDecimal manutencaoAno,
        long kmMes,
        long kmAno,
        BigDecimal custoMedioPorKmAno,
        // --- detalhes ---
        List<SituacaoVeiculo> situacoes,
        List<GastoVeiculo> gastosPorVeiculo,
        List<GastoMes> gastosPorMes,
        List<SituacaoVeiculo> alertasRevisao
) {
    public enum Estado { NA_RUA, MANUTENCAO, DISPONIVEL, INATIVO }

    /** Linha de "quem está com qual viatura". */
    public record SituacaoVeiculo(
            Long veiculoId,
            String placa,
            String modelo,
            String cidade,
            Estado estado,
            String motorista,          // quem está com a viatura (saída aberta) ou último motorista
            String destino,            // destino da saída aberta
            LocalDate desde,
            LocalTime horaSaida,
            Integer kmAtual,
            LocalDate ultimaSaida,
            String ultimaManutencao,   // tipo · descrição da última manutenção
            LocalDate dataUltimaManutencao,
            Integer proximaRevisaoKm,
            LocalDate proximaRevisaoData,
            boolean revisaoVencida
    ) {}

    /** Gasto no ano por veículo. */
    public record GastoVeiculo(
            Long veiculoId,
            String placa,
            String modelo,
            BigDecimal combustivel,
            BigDecimal litros,
            BigDecimal manutencao,
            long manutencoes,
            BigDecimal total,
            long kmRodados,
            long saidas,
            BigDecimal custoPorKm
    ) {}

    /** Gasto por mês (últimos 12 meses). */
    public record GastoMes(int ano, int mes, String rotulo, BigDecimal combustivel, BigDecimal manutencao) {
        public BigDecimal total() { return combustivel.add(manutencao); }
    }

    public BigDecimal totalMes() { return combustivelMes.add(manutencaoMes); }
    public BigDecimal totalAno() { return combustivelAno.add(manutencaoAno); }
}

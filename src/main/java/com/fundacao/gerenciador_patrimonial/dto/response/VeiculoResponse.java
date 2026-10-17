package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Veiculo;
import com.fundacao.gerenciador_patrimonial.domain.enums.Combustivel;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VeiculoResponse(
        Long id,
        Long patrimonioId,
        String numeroTombo,
        String placa,
        String modelo,
        String marca,
        String chassi,
        String renavam,
        String ano,
        String cidade,
        Combustivel combustivelPadrao,
        Integer kmAtual,
        boolean ativo,
        String rotulo,
        // --- patrimônio ---
        SituacaoPatrimonio situacao,
        Conservacao conservacao,
        LocalDate dataCompra,
        BigDecimal valorCompra,
        String notaFiscal,
        String observacao,
        Long lotacaoId,
        String lotacaoUpm,
        String lotacaoNome,
        Long responsavelId,
        String responsavelNome,
        // --- operação ---
        boolean naRua,
        LocalDate ultimaSaida
) {
    public static VeiculoResponse from(Veiculo v, boolean naRua, LocalDate ultimaSaida) {
        Patrimonio p = v.getPatrimonio();
        return new VeiculoResponse(
                v.getId(), p.getId(), p.getNumeroTombo(),
                v.getPlaca(), v.getModelo(), v.getMarca(), v.getChassi(), v.getRenavam(),
                v.getAno(), v.getCidade(), v.getCombustivelPadrao(), v.getKmAtual(), v.isAtivo(), v.getRotulo(),
                p.getSituacao(), p.getConservacao(), p.getDataCompra(), p.getValorCompra(),
                p.getNotaFiscal(), p.getObservacao(),
                p.getLotacao() != null ? p.getLotacao().getId() : null,
                p.getLotacao() != null ? p.getLotacao().getUpm() : null,
                p.getLotacao() != null ? p.getLotacao().getNome() : null,
                p.getResponsavel() != null ? p.getResponsavel().getId() : null,
                p.getResponsavel() != null ? p.getResponsavel().getNomeCompleto() : null,
                naRua, ultimaSaida);
    }

    public boolean baixado() { return situacao == SituacaoPatrimonio.BAIXADO; }
}

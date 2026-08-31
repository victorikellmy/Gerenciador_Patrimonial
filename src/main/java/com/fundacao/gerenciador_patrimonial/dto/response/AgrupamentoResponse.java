package com.fundacao.gerenciador_patrimonial.dto.response;

import java.math.BigDecimal;

/**
 * Linha de um agrupamento genérico usado em dashboard/relatórios.
 *
 * @param chave     valor do agrupamento (ex.: "COMPUTADOR", "UPM-001", "ÓTIMO")
 * @param quantidade número de itens no grupo
 * @param valorTotal soma opcional (null quando não se aplica — ex.: contagem por conservação)
 */
public record AgrupamentoResponse(
        String chave,
        long quantidade,
        BigDecimal valorTotal
) {
    public static AgrupamentoResponse of(String chave, long qtd) {
        return new AgrupamentoResponse(chave, qtd, null);
    }

    public static AgrupamentoResponse of(String chave, long qtd, BigDecimal valor) {
        return new AgrupamentoResponse(chave, qtd, valor);
    }
}

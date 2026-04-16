package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload de criação/atualização de Patrimônio.
 *
 * <p>Situação não é aceita aqui — é controlada por endpoints específicos
 * (baixa, movimentação). Isso previne que um cliente mal-intencionado
 * "reviva" um bem baixado via PUT comum.</p>
 */
public record PatrimonioRequest(
        @Size(max = 30) String numeroTombo,

        @NotBlank(message = "Descrição é obrigatória")
        @Size(max = 255)
        String descricao,

        @Size(max = 60) String categoria,

        LocalDate dataCompra,

        @PositiveOrZero(message = "Valor de compra não pode ser negativo")
        BigDecimal valorCompra,

        Conservacao conservacao,

        @Size(max = 60) String notaFiscal,

        @NotNull(message = "Lotação é obrigatória")
        Long lotacaoId,

        @NotNull(message = "Responsável é obrigatório")
        Long responsavelId
) {}

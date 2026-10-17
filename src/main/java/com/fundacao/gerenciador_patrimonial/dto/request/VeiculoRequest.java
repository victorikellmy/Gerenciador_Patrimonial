package com.fundacao.gerenciador_patrimonial.dto.request;

import com.fundacao.gerenciador_patrimonial.domain.enums.Combustivel;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cadastro/edição de veículo. Na criação, ou se vincula a um patrimônio já
 * existente ({@code patrimonioId}) ou os campos de patrimônio (lotação,
 * responsável, tombo, data/valor de aquisição...) criam um novo bem na
 * categoria VEICULO.
 */
public record VeiculoRequest(
        Long patrimonioId,

        @NotBlank(message = "Placa é obrigatória") @Size(max = 20) String placa,
        @NotBlank(message = "Modelo é obrigatório") @Size(max = 60) String modelo,
        @Size(max = 60) String marca,
        @Size(max = 50) String chassi,
        @Size(max = 20) String renavam,
        @Size(max = 10) String ano,
        @Size(max = 60) String cidade,
        Combustivel combustivelPadrao,
        @PositiveOrZero(message = "Km não pode ser negativo") Integer kmAtual,

        // --- dados do patrimônio (usados quando patrimonioId é nulo; na edição atualizam o bem) ---
        @Size(max = 30) String numeroTombo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataCompra,
        @PositiveOrZero(message = "Valor não pode ser negativo") BigDecimal valorCompra,
        Conservacao conservacao,
        @Size(max = 60) String notaFiscal,
        @Size(max = 1000) String observacao,
        Long lotacaoId,
        Long responsavelId
) {}

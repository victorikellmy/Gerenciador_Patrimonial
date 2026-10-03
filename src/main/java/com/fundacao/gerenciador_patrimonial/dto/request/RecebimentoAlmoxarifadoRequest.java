package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload enviado pelo Almoxarifado em
 * {@code POST /api/integracao/almoxarifado/recebimentos}
 * (espelho de {@code RecebimentoPatrimonioPayload} — ver docs/INTEGRACAO_PATRIMONIO.md
 * no repositório do Almoxarifado).
 */
public record RecebimentoAlmoxarifadoRequest(
        @Size(max = 30) String origem,

        @NotNull(message = "compraId é obrigatório")
        Long compraId,

        @Size(max = 30)  String numeroDocumento,
        @Size(max = 40)  String numeroSgd,
        @Size(max = 255) String assunto,
        @Size(max = 255) String solicitanteDocumento,
        @Size(max = 150) String fornecedor,
        @Size(max = 60)  String numeroNotaFiscal,

        LocalDateTime dataRecebimento,
        LocalDate dataCompra,
        BigDecimal valorTotal,

        @Size(max = 120) String retiradoPor,
        @Size(max = 150) String setorDestino,
        @Size(max = 30)  String setorDestinoCentroCusto,
        @Size(max = 120) String registradoPor,
        @Size(max = 1000) String observacao,

        @Valid List<Item> itens,
        @Valid List<Anexo> anexos
) {
    public static final String ORIGEM_PADRAO = "ALMOXARIFADO";

    public record Item(
            @NotBlank(message = "descricao do item é obrigatória")
            @Size(max = 255) String descricao,
            @Size(max = 60) String codigoSku,
            Integer quantidade,
            BigDecimal valorUnitario
    ) {
        public int quantidadeOuUm() {
            return quantidade == null || quantidade < 1 ? 1 : quantidade;
        }
    }

    public record Anexo(
            @Size(max = 30) String tipo,
            @Size(max = 255) String nomeOriginal,
            @Size(max = 100) String contentType,
            Long tamanhoBytes,
            String conteudoBase64
    ) {}

    public String origemOuPadrao() {
        return origem == null || origem.isBlank() ? ORIGEM_PADRAO : origem.trim().toUpperCase();
    }
}

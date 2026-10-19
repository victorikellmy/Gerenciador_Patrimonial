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

        @Valid @Size(max = 200, message = "no máximo 200 itens por recebimento") List<Item> itens,
        @Valid @Size(max = 5, message = "no máximo 5 anexos por recebimento") List<Anexo> anexos
) {
    public static final String ORIGEM_PADRAO = "ALMOXARIFADO";

    public record Item(
            @NotBlank(message = "descricao do item é obrigatória")
            @Size(max = 255) String descricao,
            @Size(max = 60) String codigoSku,
            // Cada unidade vira um bem patrimonial (cadastro um a um); limite evita expansão sem fim.
            @jakarta.validation.constraints.Max(value = 1000, message = "quantidade máxima por item é 1000") Integer quantidade,
            @jakarta.validation.constraints.PositiveOrZero BigDecimal valorUnitario
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
            // 10 MB em base64 ≈ 13,4 milhões de caracteres (com quebras MIME); recusa antes de decodificar.
            @Size(max = 14_500_000, message = "anexo acima de 10 MB") String conteudoBase64
    ) {}

    public String origemOuPadrao() {
        return origem == null || origem.isBlank() ? ORIGEM_PADRAO : origem.trim().toUpperCase();
    }
}

package com.fundacao.gerenciador_patrimonial.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Payload para mover um patrimônio para outra lotação e/ou responsável.
 * Ambos opcionais, mas pelo menos um deve ser informado (validação no service).
 */
public record MovimentacaoRequest(
        Long novaLotacaoId,
        Long novoResponsavelId,
        @Size(max = 500) String observacao
) {}

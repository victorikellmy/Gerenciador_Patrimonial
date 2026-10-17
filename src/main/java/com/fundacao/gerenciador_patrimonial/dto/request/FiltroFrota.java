package com.fundacao.gerenciador_patrimonial.dto.request;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Filtro das listagens gerais da frota (abastecimentos, manutenções, deslocamentos). */
public record FiltroFrota(
        Long veiculoId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
        String texto,
        Boolean somenteAbertos
) {
    public String textoOuNulo() {
        return texto == null || texto.isBlank() ? null : texto.trim().toLowerCase();
    }

    public boolean vazio() {
        return veiculoId == null && de == null && ate == null && textoOuNulo() == null
                && !Boolean.TRUE.equals(somenteAbertos);
    }
}

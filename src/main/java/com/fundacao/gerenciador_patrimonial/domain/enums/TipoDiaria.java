package com.fundacao.gerenciador_patrimonial.domain.enums;

/** Destino da viagem para efeito do valor da diária (colunas do Anexo I da Portaria 001/2019). */
public enum TipoDiaria {
    CAPITAL("Capital"),
    INTERIOR_ESTADO("Interior do Estado"),
    INTERIOR_OUTRO_ESTADO("Interior de outro Estado");

    private final String rotulo;

    TipoDiaria(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }
}

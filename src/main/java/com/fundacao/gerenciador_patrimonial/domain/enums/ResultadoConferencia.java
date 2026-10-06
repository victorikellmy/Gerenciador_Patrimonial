package com.fundacao.gerenciador_patrimonial.domain.enums;

/** Resultado da conferência física de um bem durante o levantamento patrimonial. */
public enum ResultadoConferencia {
    /** Ainda não conferido. */
    PENDENTE("Pendente"),
    /** Localizado e em ordem. */
    OK("OK"),
    /** Localizado, porém com avaria registrada. */
    AVARIADO("Avariado"),
    /** Não encontrado no local esperado nem informado em outro local. */
    NAO_LOCALIZADO("Não localizado");

    private final String rotulo;

    ResultadoConferencia(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }

    public boolean isConferido() { return this != PENDENTE; }
}

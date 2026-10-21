package com.fundacao.gerenciador_patrimonial.domain.enums;

/** Andamento de um sinistro acionado no seguro do veículo. */
public enum SituacaoSinistro {
    ABERTO("Aberto"),
    EM_ANALISE("Em análise"),
    INDENIZADO("Indenizado"),
    NEGADO("Negado"),
    ENCERRADO("Encerrado");

    private final String rotulo;

    SituacaoSinistro(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }

    /** Ainda depende da seguradora (aparece nos alertas da gestão). */
    public boolean emAndamento() { return this == ABERTO || this == EM_ANALISE; }
}

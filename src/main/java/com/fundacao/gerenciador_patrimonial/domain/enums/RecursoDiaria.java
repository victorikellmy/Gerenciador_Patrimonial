package com.fundacao.gerenciador_patrimonial.domain.enums;

import java.text.Normalizer;

/** Origem do dinheiro da diária: recurso próprio da Fundação, convênio ou outra fonte. */
public enum RecursoDiaria {
    PROPRIO("Próprio"),
    CONVENIO("Convênio"),
    OUTRA("Outra");

    private final String rotulo;

    RecursoDiaria(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }

    public static RecursoDiaria de(String texto) {
        if (texto == null) return PROPRIO;
        String t = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase();
        for (RecursoDiaria r : values()) if (r.name().equals(t)) return r;
        if (t.startsWith("OUTR")) return OUTRA;
        return t.startsWith("CONV") ? CONVENIO : PROPRIO;
    }
}

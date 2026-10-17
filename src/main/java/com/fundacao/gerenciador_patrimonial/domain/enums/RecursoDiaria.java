package com.fundacao.gerenciador_patrimonial.domain.enums;

import java.text.Normalizer;

/** Origem do dinheiro da diária: recurso próprio da Fundação ou convênio. */
public enum RecursoDiaria {
    PROPRIO("Próprio"),
    CONVENIO("Convênio");

    private final String rotulo;

    RecursoDiaria(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }

    public static RecursoDiaria de(String texto) {
        if (texto == null) return PROPRIO;
        String t = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase();
        for (RecursoDiaria r : values()) if (r.name().equals(t)) return r;
        return t.startsWith("CONV") ? CONVENIO : PROPRIO;
    }
}

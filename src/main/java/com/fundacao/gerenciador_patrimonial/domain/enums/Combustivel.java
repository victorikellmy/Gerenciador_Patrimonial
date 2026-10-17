package com.fundacao.gerenciador_patrimonial.domain.enums;

import java.text.Normalizer;

/** Combustível de um abastecimento (ou o padrão do veículo). */
public enum Combustivel {
    GASOLINA("Gasolina"),
    ETANOL("Álcool / Etanol"),
    DIESEL("Diesel"),
    GNV("GNV");

    private final String rotulo;

    Combustivel(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }

    /** Aceita o nome do enum ou o texto livre do sistema antigo ("Alcool", "Gasolina"...). */
    public static Combustivel de(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String t = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toUpperCase();
        for (Combustivel c : values()) if (c.name().equals(t)) return c;
        if (t.startsWith("ALC") || t.startsWith("ETAN")) return ETANOL;
        if (t.startsWith("DIES")) return DIESEL;
        if (t.startsWith("GNV") || t.startsWith("GAS N")) return GNV;
        return GASOLINA;
    }
}

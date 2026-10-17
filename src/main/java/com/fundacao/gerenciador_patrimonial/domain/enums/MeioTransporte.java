package com.fundacao.gerenciador_patrimonial.domain.enums;

import java.text.Normalizer;

public enum MeioTransporte {
    VEICULO_OFICIAL("Veículo oficial"),
    AEREO("Aéreo"),
    RODOVIARIO("Rodoviário");

    private final String rotulo;

    MeioTransporte(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }

    public static MeioTransporte de(String texto) {
        if (texto == null) return VEICULO_OFICIAL;
        String t = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase();
        for (MeioTransporte m : values()) if (m.name().equals(t)) return m;
        if (t.startsWith("AER")) return AEREO;
        if (t.startsWith("ROD")) return RODOVIARIO;
        return VEICULO_OFICIAL;
    }
}

package com.fundacao.gerenciador_patrimonial.domain.enums;

import java.text.Normalizer;

/** Tipo de serviço de manutenção/revisão registrado para um veículo. */
public enum TipoServicoVeiculo {
    REVISAO("Revisão"),
    CONSERTO("Conserto"),
    TROCA_OLEO("Troca de óleo"),
    BALANCEAMENTO("Balanceamento / alinhamento"),
    PNEUS("Pneus"),
    FREIOS("Freios"),
    ELETRICA("Elétrica"),
    LAVA_JATO("Lava-jato"),
    OUTRO("Outro");

    private final String rotulo;

    TipoServicoVeiculo(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }

    /** Aceita o nome do enum ou o texto livre do sistema antigo ("Troca de Óleo", "Lava Jato"...). */
    public static TipoServicoVeiculo de(String texto) {
        if (texto == null || texto.isBlank()) return OUTRO;
        String t = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toUpperCase();
        for (TipoServicoVeiculo s : values()) if (s.name().equals(t)) return s;
        if (t.startsWith("CONS")) return CONSERTO;
        if (t.startsWith("REV")) return REVISAO;
        if (t.startsWith("TROCA")) return TROCA_OLEO;
        if (t.startsWith("BALAN") || t.startsWith("ALINH")) return BALANCEAMENTO;
        if (t.startsWith("PNEU")) return PNEUS;
        if (t.startsWith("FREI")) return FREIOS;
        if (t.startsWith("ELET")) return ELETRICA;
        if (t.startsWith("LAVA")) return LAVA_JATO;
        return OUTRO;
    }
}

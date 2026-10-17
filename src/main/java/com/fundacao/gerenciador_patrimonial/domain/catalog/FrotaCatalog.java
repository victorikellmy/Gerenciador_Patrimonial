package com.fundacao.gerenciador_patrimonial.domain.catalog;

import java.util.List;

/**
 * Listas fixas da frota (mesmas opções do sistema Transportes FPTO, para que
 * os registros importados e os novos usem o mesmo vocabulário).
 */
public final class FrotaCatalog {

    /** Categoria de patrimônio usada para todo veículo. */
    public static final String CATEGORIA_VEICULO = "VEICULO";

    /** Motivos de saída do veículo. Gravado como texto para preservar o histórico antigo. */
    public static final List<String> MOTIVOS_DESLOCAMENTO = List.of(
            "Apoio",
            "Assistência",
            "Entregar/buscar Documentos",
            "Lava-Jato",
            "Mecânica",
            "Outro");

    /** Motivos de cautela (entrega do veículo a condutor externo). */
    public static final List<String> MOTIVOS_CAUTELA = List.of(
            "Cautela",
            "Apoio",
            "Assistência",
            "Entregar/buscar Documentos",
            "Lava-Jato",
            "Mecânica",
            "Outro");

    /** Unidades usadas como lotação de deslocamentos (lista do Transportes FPTO). */
    public static final List<String> LOTACOES = List.of(
            "FPTO - PALMAS",
            "FPTO - ARAGUAINA",
            "FPTO - GURUPI",
            "1º BPM - PALMAS",
            "2º BPM - ARAGUAINA",
            "3º BPM - PEDRO AFONSO",
            "4º BPM - GURUPI",
            "5º BPM - PORTO NACIONAL",
            "6º BPM - TAQUARALTO",
            "7º BPM - GUARAI",
            "8º BPM - PARAISO",
            "9º BPM - ARAGUATINS",
            "1º CIPM - ARRAIAS",
            "2º CIPM - DIANOPOLIS",
            "3º CIPM - COLINAS",
            "5º CIPM - TOCANTINOPOLIS",
            "6º CIPM - MIRACEMA",
            "2º Pelotao - PALMEIROPOLIS");

    private FrotaCatalog() {}
}

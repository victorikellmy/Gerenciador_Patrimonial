package com.fundacao.gerenciador_patrimonial.service.dominio;

import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Grupos do Patrimônio no AD. O nome no AD é {@code app.ad.prefixo-grupo + sufixo}
 * (ex.: {@code Patrimonio-Fiscal}).
 *
 * <p>A ordem da declaração é a prioridade: quem estiver em mais de um grupo de perfil
 * (alteração feita fora do sistema) fica com o primeiro. {@link #ABASTECER} não é perfil,
 * é a permissão extra {@code Usuario.podeAbastecer}.</p>
 */
public enum GrupoDominio {
    ADMINISTRADOR(Perfil.ADMINISTRADOR, "Administrador", "Patrimônio: acesso total, gestão de usuários"),
    PRESIDENTE(Perfil.PRESIDENTE, "Presidente", "Patrimônio: defere/indefere diárias, consultas"),
    FINANCEIRO(Perfil.FINANCEIRO, "Financeiro", "Patrimônio: paga diárias, consultas"),
    FISCAL(Perfil.FISCAL, "Fiscal", "Patrimônio: operação (bens, frota, diárias), sem exclusões"),
    GERENTE(Perfil.GERENTE, "Gerente", "Patrimônio: somente leitura e painel da frota"),
    MOTORISTA(Perfil.MOTORISTA, "Motorista", "Patrimônio: só o módulo Frota"),
    ABASTECER(null, "Abastecer", "Patrimônio: permissão extra para registrar abastecimentos");

    private final Perfil perfil;
    private final String sufixo;
    private final String descricao;

    GrupoDominio(Perfil perfil, String sufixo, String descricao) {
        this.perfil = perfil;
        this.sufixo = sufixo;
        this.descricao = descricao;
    }

    public Perfil perfil() { return perfil; }
    public String descricao() { return descricao; }
    public String nome(String prefixo) { return prefixo + sufixo; }

    public static Optional<GrupoDominio> doPerfil(Perfil perfil) {
        return Arrays.stream(values()).filter(g -> g.perfil != null && g.perfil == perfil).findFirst();
    }

    public static Optional<GrupoDominio> doNome(String prefixo, String nome) {
        return Arrays.stream(values()).filter(g -> g.nome(prefixo).equalsIgnoreCase(nome)).findFirst();
    }

    /** Perfil de quem está nestes grupos; sem grupo de perfil = {@link Perfil#USUARIO}. */
    public static Perfil perfilDe(Collection<GrupoDominio> grupos) {
        return Arrays.stream(values()).filter(g -> g.perfil != null && grupos.contains(g))
                .map(GrupoDominio::perfil).findFirst().orElse(Perfil.USUARIO);
    }

    /** Grupos que dão exatamente este perfil e esta permissão extra. */
    public static Set<GrupoDominio> para(Perfil perfil, boolean podeAbastecer) {
        Set<GrupoDominio> grupos = EnumSet.noneOf(GrupoDominio.class);
        doPerfil(perfil).ifPresent(grupos::add);
        if (podeAbastecer) grupos.add(ABASTECER);
        return grupos;
    }
}

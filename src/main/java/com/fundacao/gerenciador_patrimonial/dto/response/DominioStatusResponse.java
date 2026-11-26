package com.fundacao.gerenciador_patrimonial.dto.response;

import java.util.List;

/**
 * Situação do login pela rede (AD) para a tela "Grupos da rede".
 *
 * @param conectado o AD respondeu e aceitou a conta de serviço
 * @param erro      motivo quando não conectou
 * @param grupos    grupos do Patrimônio no AD (existentes ou faltando)
 */
public record DominioStatusResponse(
        boolean habilitado,
        String dominio,
        List<String> servidores,
        String ouGrupos,
        boolean conectado,
        String erro,
        List<Grupo> grupos
) {
    /** @param chave perfil (ADMINISTRADOR, FISCAL...) ou ABASTECER */
    public record Grupo(String chave, String nome, String descricao, boolean existe, int membros) {}
}

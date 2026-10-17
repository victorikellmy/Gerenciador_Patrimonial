package com.fundacao.gerenciador_patrimonial.dto.response;

import com.fundacao.gerenciador_patrimonial.domain.entity.Profissional;

import java.math.BigDecimal;

public record ProfissionalResponse(
        Long id,
        String nome,
        String matricula,
        String lotacao,
        Long nivelId,
        String nivelDescricao,
        BigDecimal valorCapitais,
        BigDecimal valorInteriorEstado,
        BigDecimal valorInteriorOutroEstado,
        String cpf,
        String rg,
        String orgaoExpedidor,
        String dadosBancarios,
        boolean ativo,
        String cadastradoPor
) {
    public static ProfissionalResponse from(Profissional p) {
        var n = p.getNivel();
        return new ProfissionalResponse(p.getId(), p.getNome(), p.getMatricula(), p.getLotacao(),
                n != null ? n.getId() : null, n != null ? n.getDescricao() : null,
                n != null ? n.getValorCapitais() : null, n != null ? n.getValorInteriorEstado() : null,
                n != null ? n.getValorInteriorOutroEstado() : null,
                p.getCpf(), p.getRg(), p.getOrgaoExpedidor(), p.getDadosBancarios(), p.isAtivo(), p.getCadastradoPor());
    }
}

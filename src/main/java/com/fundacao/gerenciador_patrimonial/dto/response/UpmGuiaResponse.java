package com.fundacao.gerenciador_patrimonial.dto.response;

import java.util.List;

/** Grupo do guia de levantamento: uma UPM e seus locais com o andamento da conferência. */
public record UpmGuiaResponse(
        String upm,
        List<LocalLevantamentoResumo> locais
) {}

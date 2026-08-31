package com.fundacao.gerenciador_patrimonial.service.importer;

import java.util.List;

/**
 * Resultado agregado de uma importação de planilha.
 *
 * @param total           total de linhas lidas
 * @param importados      linhas que geraram patrimônios com sucesso
 * @param ignorados       linhas vazias/em branco que foram puladas
 * @param erros           descrição de cada linha que falhou (linha: motivo)
 * @param lotacoesCriadas lotações novas criadas durante a carga
 * @param responsaveisCriados responsáveis novos criados durante a carga
 */
public record ImportResult(
        int total,
        int importados,
        int ignorados,
        List<String> erros,
        int lotacoesCriadas,
        int responsaveisCriados
) {}

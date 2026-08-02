package com.fundacao.gerenciador_patrimonial.service.importer;

/**
 * Callback de progresso da importação — notificado ao início (0/total)
 * e ao final de cada chunk processado.
 */
@FunctionalInterface
public interface ProgressoImportacao {

    ProgressoImportacao NENHUM = (processadas, total) -> { };

    void atualizar(int processadas, int total);
}

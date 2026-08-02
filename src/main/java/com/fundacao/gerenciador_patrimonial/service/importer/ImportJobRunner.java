package com.fundacao.gerenciador_patrimonial.service.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Executa a importação em thread do executor ({@code @Async}).
 *
 * <p>Bean separado do {@link ImportJobService} de propósito: a anotação
 * {@code @Async} só funciona através do proxy Spring — uma auto-invocação
 * dentro do próprio service rodaria síncrona. O arquivo temporário é
 * removido aqui, ao final, independentemente do resultado.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImportJobRunner {

    private final ExcelImportService excelImportService;

    @Async
    public void executar(Path arquivo, String nomeSheet,
                         ProgressoImportacao progresso,
                         Consumer<ImportResult> aoConcluir,
                         Consumer<Exception> aoFalhar) {
        try {
            aoConcluir.accept(excelImportService.importar(arquivo, nomeSheet, progresso));
        } catch (Exception e) {
            log.error("Importação assíncrona falhou: {}", e.getMessage(), e);
            aoFalhar.accept(e);
        } finally {
            try {
                Files.deleteIfExists(arquivo);
            } catch (IOException e) {
                log.warn("Não foi possível remover o arquivo temporário {}: {}", arquivo, e.getMessage());
            }
        }
    }
}

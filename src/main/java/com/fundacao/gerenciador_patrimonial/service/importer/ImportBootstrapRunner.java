package com.fundacao.gerenciador_patrimonial.service.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Executa a importação automaticamente no startup <b>apenas</b> quando
 * a flag {@code app.importacao.habilitada=true} estiver presente.
 *
 * <p>Isso previne re-importações acidentais em produção.</p>
 */
@Component
@ConditionalOnProperty(name = "app.importacao.habilitada", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ImportBootstrapRunner implements CommandLineRunner {

    private final ExcelImportService importService;

    @Value("${app.importacao.arquivo}")
    private String caminhoArquivo;

    @Override
    public void run(String... args) throws Exception {
        Path arquivo = Path.of(caminhoArquivo);
        if (!Files.exists(arquivo)) {
            log.warn("Arquivo de importação não encontrado: {} — pulando carga inicial.", arquivo.toAbsolutePath());
            return;
        }

        log.info("Iniciando carga inicial a partir de {}", arquivo.toAbsolutePath());
        try (InputStream is = Files.newInputStream(arquivo)) {
            ImportResult result = importService.importar(is, null);
            log.info("Carga inicial: {} importados, {} ignorados, {} erros, {} lotações novas, {} responsáveis novos",
                    result.importados(), result.ignorados(), result.erros().size(),
                    result.lotacoesCriadas(), result.responsaveisCriados());

            if (!result.erros().isEmpty()) {
                log.warn("Erros encontrados:");
                result.erros().forEach(e -> log.warn("  - {}", e));
            }
        }
    }
}

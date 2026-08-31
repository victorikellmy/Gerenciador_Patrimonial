package com.fundacao.gerenciador_patrimonial.service.importer;

import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Executa a carga inicial da planilha de patrimônio no startup,
 * quando a flag {@code app.importacao.habilitada=true} estiver presente.
 *
 * <p><b>Idempotência</b>: só roda se o banco ainda não tiver nenhum
 * patrimônio cadastrado. Isso torna seguro deixar a flag ligada em
 * ambientes de apresentação/demo — o seed acontece no primeiro start
 * e é ignorado nos subsequentes.</p>
 *
 * <p>O caminho aceita tanto <b>classpath</b> quanto <b>filesystem</b>:</p>
 * <pre>
 *   app.importacao.caminho: classpath:seed/patrimonio-inicial.xlsx   (default — demo)
 *   app.importacao.caminho: file:./data/patrimonio.xlsx              (prod — arquivo externo)
 *   app.importacao.caminho: ./data/patrimonio.xlsx                   (prefixo file: implícito)
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "app.importacao.habilitada", havingValue = "true")
@Order(2) // roda DEPOIS do AdminBootstrapRunner (@Order 1) para ter auditor "admin" pronto
@RequiredArgsConstructor
@Slf4j
public class ImportBootstrapRunner implements CommandLineRunner {

    private final ExcelImportService importService;
    private final PatrimonioRepository patrimonioRepo;
    private final ResourceLoader resourceLoader;

    @Value("${app.importacao.caminho:classpath:seed/patrimonio-inicial.xlsx}")
    private String caminho;

    @Override
    public void run(String... args) throws Exception {
        // --- Idempotência: se já há dados, não recarrega ---
        long existentes = patrimonioRepo.count();
        if (existentes > 0) {
            log.info("Seed de planilha ignorado — já existem {} patrimônios cadastrados.", existentes);
            return;
        }

        // --- Resolve classpath: ou file: ---
        String location = caminho.startsWith("classpath:") || caminho.startsWith("file:")
                ? caminho
                : "file:" + caminho;

        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("Arquivo de seed não encontrado em '{}' — pulando carga inicial.", location);
            return;
        }

        log.info("=============================================================");
        log.info("Iniciando carga inicial de demonstração a partir de {}", location);
        log.info("=============================================================");

        try (InputStream is = resource.getInputStream()) {
            ImportResult result = importService.importar(is, null);
            log.info("Carga demo concluída: {} importados, {} ignorados, {} erros, {} lotações novas, {} responsáveis novos",
                    result.importados(), result.ignorados(), result.erros().size(),
                    result.lotacoesCriadas(), result.responsaveisCriados());

            if (!result.erros().isEmpty()) {
                log.warn("Algumas linhas foram ignoradas:");
                result.erros().stream().limit(20).forEach(e -> log.warn("  - {}", e));
                if (result.erros().size() > 20) {
                    log.warn("  ... e mais {} erros omitidos.", result.erros().size() - 20);
                }
            }
        } catch (Exception e) {
            log.error("Falha ao carregar planilha de seed: {}", e.getMessage(), e);
        }
    }
}

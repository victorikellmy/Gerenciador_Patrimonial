package com.fundacao.gerenciador_patrimonial.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Roda a importação do Transportes uma vez a cada boot, quando
 * {@code app.frota.transportes.url} estiver definida e
 * {@code app.frota.transportes.importar-no-boot} não for {@code false}.
 * Como a importação é idempotente, só entram os lançamentos ainda não copiados.
 */
@Component
@ConditionalOnProperty(name = "app.frota.transportes.importar-no-boot", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class ImportadorTransportesRunner implements ApplicationRunner {

    private final ImportadorTransportesService importador;

    @Override
    public void run(ApplicationArguments args) {
        if (!importador.configurado()) {
            log.info("Importação do Transportes desligada (TRANSPORTES_DB_URL não definida).");
            return;
        }
        try {
            var r = importador.importar();
            log.info("Transportes → Frota: {} veículo(s) novo(s), {} lançamento(s) importado(s).", r.veiculosNovos(), r.total());
        } catch (RuntimeException e) {
            // Não derruba a aplicação: o administrador pode repetir pela tela da frota.
            log.error("Importação do Transportes falhou no boot: {}", e.getMessage());
        }
    }
}

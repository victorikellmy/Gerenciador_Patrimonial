package com.fundacao.gerenciador_patrimonial.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita execução assíncrona ({@code @Async}).
 *
 * <p>Usada pela gravação de auditoria e pela importação de planilha em
 * background. O executor é o padrão do Spring Boot
 * ({@code ThreadPoolTaskExecutor}), ajustável via propriedades
 * {@code spring.task.execution.*} no application.yml.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}

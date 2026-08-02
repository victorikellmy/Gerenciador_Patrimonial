package com.fundacao.gerenciador_patrimonial.service.importer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registro em memória dos jobs de importação assíncrona.
 *
 * <p>Cada upload vira um job com UUID; o processamento roda no executor
 * ({@link ImportJobRunner}) e o estado/progresso é consultado por polling
 * (tela de status e endpoint REST). Jobs concluídos são retidos por
 * {@link #RETENCAO} e limpos de forma oportunista a cada novo início —
 * suficiente para um registro efêmero de single-node (reinício do app
 * descarta os jobs, como qualquer estado em memória).</p>
 */
@Service
@RequiredArgsConstructor
public class ImportJobService {

    private static final Duration RETENCAO = Duration.ofHours(2);

    public enum Estado { EM_ANDAMENTO, CONCLUIDO, FALHOU }

    /** Snapshot imutável exposto para tela/JSON. */
    public record StatusJob(
            String id,
            Estado estado,
            int processadas,
            int total,
            int percentual,
            String arquivoNome,
            String sheet,
            LocalDateTime inicio,
            LocalDateTime fim,
            ImportResult resultado,
            String mensagemErro
    ) {}

    /** Estado mutável interno — atualizado pelo thread do executor. */
    private static final class Job {
        final String arquivoNome;
        final String sheet;
        final LocalDateTime inicio = LocalDateTime.now();
        final AtomicInteger processadas = new AtomicInteger();
        volatile int total = -1;                 // -1 = ainda lendo a planilha
        volatile Estado estado = Estado.EM_ANDAMENTO;
        volatile ImportResult resultado;
        volatile String erro;
        volatile LocalDateTime fim;

        Job(String arquivoNome, String sheet) {
            this.arquivoNome = arquivoNome;
            this.sheet = sheet;
        }
    }

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ImportJobRunner runner;

    /**
     * Registra e dispara um job. O chamador entrega a posse do arquivo
     * temporário — o runner o remove ao final.
     */
    public String iniciar(Path arquivo, String nomeSheet, String arquivoNome) {
        limparAntigos();

        String id = UUID.randomUUID().toString();
        Job job = new Job(arquivoNome, nomeSheet);
        jobs.put(id, job);

        runner.executar(arquivo, nomeSheet,
                (processadas, total) -> {
                    job.total = total;
                    job.processadas.set(processadas);
                },
                resultado -> {
                    job.resultado = resultado;
                    job.estado = Estado.CONCLUIDO;
                    job.fim = LocalDateTime.now();
                },
                e -> {
                    job.erro = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    job.estado = Estado.FALHOU;
                    job.fim = LocalDateTime.now();
                });
        return id;
    }

    public Optional<StatusJob> status(String id) {
        Job j = jobs.get(id);
        if (j == null) return Optional.empty();

        int processadas = j.processadas.get();
        int percentual = j.estado == Estado.CONCLUIDO ? 100
                : (j.total > 0 ? (int) (processadas * 100L / j.total) : 0);
        return Optional.of(new StatusJob(id, j.estado, processadas, Math.max(j.total, 0),
                percentual, j.arquivoNome, j.sheet, j.inicio, j.fim, j.resultado, j.erro));
    }

    private void limparAntigos() {
        LocalDateTime limite = LocalDateTime.now().minus(RETENCAO);
        jobs.entrySet().removeIf(e ->
                e.getValue().fim != null && e.getValue().fim.isBefore(limite));
    }
}

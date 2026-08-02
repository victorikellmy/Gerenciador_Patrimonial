package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.AuditoriaAcao;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.repository.AuditoriaAcaoRepository;
import com.fundacao.gerenciador_patrimonial.util.Textos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava o registro de auditoria em thread separado ({@code @Async}).
 *
 * <p>Antes, o registro usava {@code REQUIRES_NEW} no thread da request, o que
 * mantinha DUAS conexões JDBC ocupadas por operação de escrita (a transação de
 * negócio suspensa + a da auditoria). Assíncrono, a gravação sai do caminho
 * crítico e usa conexão própria só pelo tempo do INSERT.</p>
 *
 * <p>Usuário e IP são capturados pelo {@link AuditoriaService} ainda no thread
 * da request — {@code SecurityContextHolder}/{@code RequestContextHolder} não
 * estão disponíveis no thread do executor.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditoriaGravador {

    private final AuditoriaAcaoRepository repo;

    @Async
    @Transactional
    public void gravar(AcaoAuditoria acao, String entidade, Long entidadeId,
                       String descricao, String usuario, String ipOrigem) {
        try {
            repo.save(AuditoriaAcao.builder()
                    .usuario(usuario)
                    .acao(acao)
                    .entidade(entidade)
                    .entidadeId(entidadeId)
                    .descricao(Textos.truncar(descricao, 2000))
                    .ipOrigem(ipOrigem)
                    .build());
        } catch (Exception e) {
            // Falha de auditoria não deve quebrar a operação de negócio.
            log.error("Falha ao registrar auditoria ({} {} #{}): {}",
                    acao, entidade, entidadeId, e.getMessage());
        }
    }
}

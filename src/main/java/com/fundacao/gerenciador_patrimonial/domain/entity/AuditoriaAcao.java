package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registro imutável de uma ação executada por um usuário.
 *
 * <p>Diferente da auditoria do Spring Data JPA ({@code criado_por/atualizado_por}),
 * que armazena apenas o estado mais recente de cada entidade, esta tabela mantém
 * a <b>sequência completa</b> — todo CREATE, UPDATE, DELETE, movimentação e baixa.</p>
 */
@Entity
@Table(name = "auditoria_acao", indexes = {
        @Index(name = "idx_auditoria_usuario",   columnList = "usuario"),
        @Index(name = "idx_auditoria_acao",      columnList = "acao"),
        @Index(name = "idx_auditoria_entidade",  columnList = "entidade,entidade_id"),
        @Index(name = "idx_auditoria_data_hora", columnList = "data_hora")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AuditoriaAcao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AcaoAuditoria acao;

    /** Nome lógico da entidade (ex.: "Patrimonio", "Anexo", "Lotacao"). */
    @Column(nullable = false, length = 60)
    private String entidade;

    @Column(name = "entidade_id")
    private Long entidadeId;

    /** Resumo legível do que mudou (campos alterados, valores antes/depois). */
    @Column(length = 2000)
    private String descricao;

    @Column(name = "ip_origem", length = 45)
    private String ipOrigem;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @PrePersist
    void prePersist() {
        if (dataHora == null) dataHora = LocalDateTime.now();
    }
}

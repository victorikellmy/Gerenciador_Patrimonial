package com.fundacao.gerenciador_patrimonial.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Registro histórico de movimentação de um patrimônio
 * (troca de lotação ou de responsável).
 *
 * <p>Nunca é alterado após criado — trilha de auditoria.</p>
 */
@Entity
@Table(name = "movimentacao", indexes = {
        @Index(name = "idx_mov_patrimonio", columnList = "patrimonio_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Movimentacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patrimonio_id", nullable = false)
    private Patrimonio patrimonio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lotacao_origem_id")
    private Lotacao lotacaoOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lotacao_destino_id")
    private Lotacao lotacaoDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_origem_id")
    private Responsavel responsavelOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_destino_id")
    private Responsavel responsavelDestino;

    @Column(name = "data_movimentacao", nullable = false)
    private LocalDateTime dataMovimentacao;

    @Column(length = 500)
    private String observacao;

    @Column(name = "executado_por", length = 80)
    private String executadoPor;

    @PrePersist
    void prePersist() {
        if (dataMovimentacao == null) dataMovimentacao = LocalDateTime.now();
    }
}

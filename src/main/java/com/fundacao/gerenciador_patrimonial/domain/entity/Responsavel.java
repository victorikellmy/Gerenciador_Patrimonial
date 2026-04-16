package com.fundacao.gerenciador_patrimonial.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Responsável pelo patrimônio. Pessoa física que assina pelos bens.
 */
@Entity
@Table(name = "responsavel")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Responsavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_completo", nullable = false, length = 150)
    private String nomeCompleto;

    /** Matrícula funcional. Quando presente, é única. */
    @Column(length = 50, unique = true)
    private String matricula;

    @Column(length = 80)
    private String cidade;

    /** Lotação atual do responsável (pode mudar quando há transferência). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lotacao_id")
    private Lotacao lotacao;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}

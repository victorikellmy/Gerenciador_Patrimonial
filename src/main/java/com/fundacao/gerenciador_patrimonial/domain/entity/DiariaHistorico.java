package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Trilha de quem abriu, confirmou, deferiu, indeferiu ou pagou cada diária. */
@Entity
@Table(name = "diaria_historico", indexes = @Index(name = "idx_diaria_historico_diaria", columnList = "diaria_id"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DiariaHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diaria_id", nullable = false)
    private Diaria diaria;

    @Enumerated(EnumType.STRING)
    @Column(name = "de_status", length = 20)
    private StatusDiaria deStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "para_status", nullable = false, length = 20)
    private StatusDiaria paraStatus;

    @Column(nullable = false, length = 160)
    private String usuario;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime instante = LocalDateTime.now();

    @Column(length = 500)
    private String observacao;
}

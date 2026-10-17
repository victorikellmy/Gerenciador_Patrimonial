package com.fundacao.gerenciador_patrimonial.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/** Quem recebe diária: servidor/militar com nível funcional e dados bancários. */
@Entity
@Table(name = "profissional", indexes = {
        @Index(name = "idx_profissional_cpf",  columnList = "cpf"),
        @Index(name = "idx_profissional_nome", columnList = "nome")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Profissional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 30)
    private String matricula;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(length = 60)
    private String lotacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nivel_diaria_id", nullable = false)
    private NivelDiaria nivel;

    @Column(nullable = false, length = 14)
    private String cpf;

    @Column(length = 30)
    private String rg;

    @Column(name = "orgao_expedidor", length = 30)
    private String orgaoExpedidor;

    @Column(name = "dados_bancarios", length = 160)
    private String dadosBancarios;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Column(name = "cadastrado_por", length = 160)
    private String cadastradoPor;

    @Column(name = "id_transportes")
    private Integer idTransportes;
}

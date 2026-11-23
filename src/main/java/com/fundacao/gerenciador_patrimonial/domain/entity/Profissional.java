package com.fundacao.gerenciador_patrimonial.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Quem recebe diária: militar, funcionário ou prestador. O cadastro é alimentado pelo próprio
 * requerimento de diária (a cada pedido os dados informados são gravados aqui) e reaproveitado
 * na solicitação seguinte, buscando por nome, CPF ou matrícula.
 */
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

    /** Cargo, posto ou função, como sai no requerimento. */
    @Column(length = 120)
    private String cargo;

    /** Linha da tabela de valores (opcional): só serve para sugerir o valor unitário. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nivel_diaria_id")
    private NivelDiaria nivel;

    @Column(nullable = false, length = 14)
    private String cpf;

    @Column(length = 30)
    private String rg;

    @Column(name = "orgao_expedidor", length = 30)
    private String orgaoExpedidor;

    /** Texto livre herdado do Transportes; substituído pelos campos estruturados abaixo. */
    @Column(name = "dados_bancarios", length = 160)
    private String dadosBancarios;

    @Column(length = 3)
    private String banco;

    @Column(length = 15)
    private String agencia;

    @Column(name = "conta_corrente", length = 20)
    private String contaCorrente;

    @Column(length = 120)
    private String pix;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Column(name = "cadastrado_por", length = 160)
    private String cadastradoPor;

    @Column(name = "id_transportes")
    private Integer idTransportes;
}

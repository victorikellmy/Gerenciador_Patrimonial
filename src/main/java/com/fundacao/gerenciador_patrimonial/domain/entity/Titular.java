package com.fundacao.gerenciador_patrimonial.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Militar/pensionista da base da Fundação (4.317 nomes vindos do Transportes).
 * Serve ao autocomplete do campo "Titular" em deslocamentos e cautelas.
 */
@Entity
@Table(name = "titular", indexes = @Index(name = "idx_titular_nome", columnList = "nome"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Titular {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "posto_graduacao")
    private Integer postoGraduacao;

    /** M = militar, P = pensionista. */
    @Column(name = "tipo_cadastro", length = 1)
    private String tipoCadastro;

    @Column(length = 20)
    private String matricula;

    @Column(length = 2)
    private String vinculo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 15)
    private String cpf;

    @Column(name = "id_transportes")
    private Integer idTransportes;
}

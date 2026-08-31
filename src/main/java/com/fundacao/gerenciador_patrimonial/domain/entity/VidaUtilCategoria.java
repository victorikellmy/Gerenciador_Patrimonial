package com.fundacao.gerenciador_patrimonial.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tabela de referência: Vida Útil Padrão (VUT) em anos, por categoria.
 * Populada pela migração {@code V2__seed_reference_data.sql}.
 */
@Entity
@Table(name = "vida_util_categoria")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class VidaUtilCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String categoria;

    @Column(name = "vut_anos", nullable = false)
    private Integer vutAnos;
}

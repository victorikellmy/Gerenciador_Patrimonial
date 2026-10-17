package com.fundacao.gerenciador_patrimonial.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Tabela de valores de diária por nível funcional (Portaria 004/2018 GAB. Pres FPTO):
 * capitais, interior do Estado e interior de outro Estado.
 */
@Entity
@Table(name = "nivel_diaria")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class NivelDiaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String descricao;

    @Column(name = "valor_capitais", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorCapitais;

    @Column(name = "valor_interior_estado", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorInteriorEstado;

    @Column(name = "valor_interior_outro_estado", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorInteriorOutroEstado;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Column(name = "id_transportes")
    private Integer idTransportes;
}

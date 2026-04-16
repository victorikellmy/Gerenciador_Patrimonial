package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Tabela de referência: percentual de Vida Útil Decorrida (VUD)
 * associado a cada estado de conservação.
 *
 * <p>Valores oriundos da aba {@code val est cons} da planilha original.</p>
 */
@Entity
@Table(name = "percentual_conservacao")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PercentualConservacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private Conservacao conservacao;

    /** Ex.: 0.4000 para "BOM / REGULAR". */
    @Column(name = "percentual_vud", nullable = false, precision = 5, scale = 4)
    private BigDecimal percentualVud;

    @Column(length = 255)
    private String descricao;
}

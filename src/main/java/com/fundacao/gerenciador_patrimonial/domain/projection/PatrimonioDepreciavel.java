package com.fundacao.gerenciador_patrimonial.domain.projection;

import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projeção mínima usada para cálculo de depreciação em massa
 * (dashboard, relatórios agregados).
 *
 * <p>Carrega só os 5 campos que a fórmula precisa, sem hidratar o agregado
 * completo nem disparar fetch de relacionamentos. Em produção (PostgreSQL)
 * isso evita carregar dezenas de milhares de entidades para somar dois
 * BigDecimals.</p>
 *
 * <p>Construída via JPQL {@code select new ...}; ver
 * {@code PatrimonioRepository#projecaoDepreciacaoAtivos}.</p>
 */
public record PatrimonioDepreciavel(
        String categoria,
        BigDecimal valorCompra,
        LocalDate dataCompra,
        Conservacao conservacao,
        BigDecimal valorRecuperavel
) {}

package com.fundacao.gerenciador_patrimonial.domain.catalog;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Catálogo de subcategorias disponíveis para Patrimônio.
 *
 * <p>Hoje a lista é fixa em código. Quando houver tabela de domínio
 * {@code subcategoria} em produção, basta substituir {@link #disponiveis}
 * por uma consulta ao repositório — todos os chamadores continuam iguais.</p>
 */
@Component
public class SubcategoriaCatalog {

    private static final List<String> VALORES = List.of(
            "Computadores",
            "Mesas",
            "Cadeiras",
            "Ar condicionado"
    );

    public List<String> disponiveis() {
        return VALORES;
    }
}

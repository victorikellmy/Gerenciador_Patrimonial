package com.fundacao.gerenciador_patrimonial.repository.spec;

import com.fundacao.gerenciador_patrimonial.domain.entity.AuditoriaAcao;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Filtros dinâmicos da trilha de auditoria.
 *
 * <p>Substitui a query com o padrão {@code (:param is null or ...)}: aquele
 * formato gera um plano genérico no banco que ignora os índices da tabela —
 * que é append-only e só cresce. Compondo apenas os predicados efetivamente
 * informados, o plano usa os índices de {@code data_hora}/{@code usuario}.</p>
 */
public final class AuditoriaSpecifications {

    private AuditoriaSpecifications() {}

    public static Specification<AuditoriaAcao> comFiltro(String usuario,
                                                         AcaoAuditoria acao,
                                                         String entidade,
                                                         Long entidadeId,
                                                         LocalDateTime de,
                                                         LocalDateTime ate) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();

            if (usuario != null) {
                p.add(cb.like(cb.lower(root.get("usuario")),
                        "%" + usuario.toLowerCase() + "%"));
            }
            if (acao != null)       p.add(cb.equal(root.get("acao"), acao));
            if (entidade != null)   p.add(cb.equal(root.get("entidade"), entidade));
            if (entidadeId != null) p.add(cb.equal(root.get("entidadeId"), entidadeId));
            if (de != null)         p.add(cb.greaterThanOrEqualTo(root.get("dataHora"), de));
            if (ate != null)        p.add(cb.lessThanOrEqualTo(root.get("dataHora"), ate));

            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}

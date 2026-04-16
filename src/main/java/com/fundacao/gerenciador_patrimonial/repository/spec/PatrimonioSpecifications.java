package com.fundacao.gerenciador_patrimonial.repository.spec;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroPatrimonio;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Geração de filtros dinâmicos via JPA Specification.
 *
 * <p>Compor com {@code Specification.where(...).and(...)} permite
 * combinar filtros opcionais sem escrever múltiplas queries.</p>
 */
public final class PatrimonioSpecifications {

    private PatrimonioSpecifications() {}

    public static Specification<Patrimonio> comFiltro(FiltroPatrimonio f) {
        return Specification
                .where(descricaoContem(f.descricao()))
                .and(tomboIgual(f.numeroTombo()))
                .and(lotacaoIgual(f.lotacaoId()))
                .and(responsavelIgual(f.responsavelId()))
                .and(upmIgual(f.upm()))
                .and(categoriaIgual(f.categoria()))
                .and(situacaoIgual(f.situacao()))
                .and(conservacaoIgual(f.conservacao()));
    }

    public static Specification<Patrimonio> descricaoContem(String termo) {
        if (!StringUtils.hasText(termo)) return null;
        String like = "%" + termo.trim().toUpperCase() + "%";
        return (root, q, cb) -> cb.like(cb.upper(root.get("descricao")), like);
    }

    public static Specification<Patrimonio> tomboIgual(String tombo) {
        if (!StringUtils.hasText(tombo)) return null;
        return (root, q, cb) -> cb.equal(root.get("numeroTombo"), tombo.trim());
    }

    public static Specification<Patrimonio> lotacaoIgual(Long lotacaoId) {
        if (lotacaoId == null) return null;
        return (root, q, cb) -> cb.equal(root.get("lotacao").get("id"), lotacaoId);
    }

    public static Specification<Patrimonio> responsavelIgual(Long respId) {
        if (respId == null) return null;
        return (root, q, cb) -> cb.equal(root.get("responsavel").get("id"), respId);
    }

    /** Filtra por UPM (toda uma unidade, não uma sala específica). */
    public static Specification<Patrimonio> upmIgual(String upm) {
        if (!StringUtils.hasText(upm)) return null;
        return (root, q, cb) -> {
            Join<Patrimonio, Lotacao> join = root.join("lotacao");
            return cb.equal(cb.upper(join.get("upm")), upm.trim().toUpperCase());
        };
    }

    public static Specification<Patrimonio> categoriaIgual(String categoria) {
        if (!StringUtils.hasText(categoria)) return null;
        return (root, q, cb) -> cb.equal(cb.upper(root.get("categoria")), categoria.trim().toUpperCase());
    }

    public static Specification<Patrimonio> situacaoIgual(SituacaoPatrimonio s) {
        if (s == null) return null;
        return (root, q, cb) -> cb.equal(root.get("situacao"), s);
    }

    public static Specification<Patrimonio> conservacaoIgual(Conservacao c) {
        if (c == null) return null;
        return (root, q, cb) -> cb.equal(root.get("conservacao"), c);
    }
}

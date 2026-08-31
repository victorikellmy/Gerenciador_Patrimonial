package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.domain.projection.PatrimonioDepreciavel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatrimonioRepository
        extends JpaRepository<Patrimonio, Long>, JpaSpecificationExecutor<Patrimonio> {

    Optional<Patrimonio> findByNumeroTombo(String numeroTombo);

    /** Listagem paginada por situação. */
    Page<Patrimonio> findBySituacao(SituacaoPatrimonio situacao, Pageable pageable);

    /**
     * Projeção mínima dos ativos para cálculo de depreciação em massa.
     *
     * <p>Retorna apenas os 5 campos da fórmula — sem fetch de lotação,
     * responsável, anexos ou histórico. Crítico em produção (PostgreSQL):
     * substitui o antigo {@code findBySituacao(ATIVO)} que hidratava o
     * agregado completo só para somar dois {@link BigDecimal}.</p>
     */
    @Query("""
           select new com.fundacao.gerenciador_patrimonial.domain.projection.PatrimonioDepreciavel(
                  p.categoria, p.valorCompra, p.dataCompra, p.conservacao, p.valorRecuperavel)
           from Patrimonio p
           where p.situacao = com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio.ATIVO
           """)
    List<PatrimonioDepreciavel> projecaoDepreciacaoAtivos();

    // =========================================================================
    // Agregações para Dashboard / Relatórios (Sprint 4)
    //
    // Retornam arrays Object[] com (chave, quantidade) ou (chave, quantidade, soma).
    // Convertidos em DTOs pelos services.
    // =========================================================================

    /** Contagem de patrimônios por situação. */
    @Query("""
           select p.situacao, count(p)
           from Patrimonio p
           group by p.situacao
           """)
    List<Object[]> contarPorSituacao();

    /** Contagem + soma de valor por categoria (apenas ATIVO). */
    @Query("""
           select coalesce(p.categoria,'(sem categoria)'),
                  count(p),
                  coalesce(sum(p.valorCompra), 0)
           from Patrimonio p
           where p.situacao = com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio.ATIVO
           group by p.categoria
           order by count(p) desc
           """)
    List<Object[]> agruparPorCategoria();

    /** Contagem por estado de conservação (apenas ATIVO). */
    @Query("""
           select coalesce(cast(p.conservacao as string),'(não informado)'),
                  count(p)
           from Patrimonio p
           where p.situacao = com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio.ATIVO
           group by p.conservacao
           order by count(p) desc
           """)
    List<Object[]> agruparPorConservacao();

    /** Top-N UPMs por quantidade de patrimônios ativos. */
    @Query("""
           select l.upm, count(p), coalesce(sum(p.valorCompra), 0)
           from Patrimonio p
           join p.lotacao l
           where p.situacao = com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio.ATIVO
           group by l.upm
           order by count(p) desc
           """)
    List<Object[]> agruparPorUpm(Pageable pageable);

    /** Soma do valor de compra (custo de reposição) dos ativos. */
    @Query("""
           select coalesce(sum(p.valorCompra), 0)
           from Patrimonio p
           where p.situacao = com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio.ATIVO
           """)
    BigDecimal somarValorAtivos();

    /** Listagem completa para export — evita paginação. */
    @Query("""
           select p
           from Patrimonio p
           join fetch p.lotacao
           join fetch p.responsavel
           order by p.id
           """)
    List<Patrimonio> listarTudoParaRelatorio();

    /** Patrimônios vinculados a um responsável (ativos), para termo de responsabilidade. */
    @Query("""
           select p
           from Patrimonio p
           join fetch p.lotacao
           join fetch p.responsavel r
           where r.id = :responsavelId
             and p.situacao = com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio.ATIVO
           order by p.numeroTombo
           """)
    List<Patrimonio> listarAtivosDoResponsavel(Long responsavelId);

    /** Categorias distintas — alimenta dropdowns de filtro e cadastro. */
    @Query("select distinct p.categoria from Patrimonio p where p.categoria is not null order by p.categoria")
    List<String> findDistinctCategorias();

    /** Patrimônios baixados (para relatório de baixas). */
    @Query("""
           select p
           from Patrimonio p
           join fetch p.lotacao
           join fetch p.responsavel
           where p.situacao = com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio.BAIXADO
           order by p.dataBaixa desc
           """)
    List<Patrimonio> listarBaixados();
}

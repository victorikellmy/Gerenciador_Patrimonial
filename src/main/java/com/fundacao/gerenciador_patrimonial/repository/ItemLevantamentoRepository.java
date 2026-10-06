package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.ItemLevantamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemLevantamentoRepository extends JpaRepository<ItemLevantamento, Long> {

    Optional<ItemLevantamento> findByIdAndLevantamentoId(Long id, Long levantamentoId);

    Optional<ItemLevantamento> findByLevantamentoIdAndPatrimonioId(Long levantamentoId, Long patrimonioId);

    /** (resultado, quantidade) do levantamento inteiro. */
    @Query("""
           select i.resultado, count(i)
           from ItemLevantamento i
           where i.levantamento.id = :levId
           group by i.resultado
           """)
    List<Object[]> contarPorResultado(@Param("levId") Long levantamentoId);

    /** Bens encontrados em local diferente do esperado. */
    @Query("""
           select count(i)
           from ItemLevantamento i
           where i.levantamento.id = :levId
             and i.lotacaoEncontrada is not null
             and i.lotacaoEncontrada.id <> i.lotacao.id
           """)
    long contarDivergenciasDeLocal(@Param("levId") Long levantamentoId);

    /**
     * (lotacaoId, upm, nome, resultado, quantidade) agrupado pelo local ESPERADO —
     * base do guia por local.
     */
    @Query("""
           select l.id, l.upm, l.nome, i.resultado, count(i)
           from ItemLevantamento i
           join i.lotacao l
           where i.levantamento.id = :levId
           group by l.id, l.upm, l.nome, i.resultado
           order by l.upm, l.nome
           """)
    List<Object[]> resumoPorLocal(@Param("levId") Long levantamentoId);

    /** (lotacaoEncontradaId, quantidade) de bens achados em um local que não era o esperado. */
    @Query("""
           select e.id, count(i)
           from ItemLevantamento i
           join i.lotacaoEncontrada e
           where i.levantamento.id = :levId
             and e.id <> i.lotacao.id
           group by e.id
           """)
    List<Object[]> contarEncontradosForaDoEsperado(@Param("levId") Long levantamentoId);

    /** Checklist de um local: itens esperados ali OU encontrados ali. */
    @Query("""
           select distinct i
           from ItemLevantamento i
           join fetch i.patrimonio p
           join fetch i.lotacao l
           left join fetch i.lotacaoEncontrada
           left join fetch p.responsavel
           left join fetch i.foto
           where i.levantamento.id = :levId
             and (l.id = :lotacaoId or i.lotacaoEncontrada.id = :lotacaoId)
           order by p.numeroTombo, p.descricao
           """)
    List<ItemLevantamento> listarDoLocal(@Param("levId") Long levantamentoId,
                                         @Param("lotacaoId") Long lotacaoId);

    /** Itens com ocorrência: avaria, não localizado ou local divergente. */
    @Query("""
           select distinct i
           from ItemLevantamento i
           join fetch i.patrimonio p
           join fetch i.lotacao l
           left join fetch i.lotacaoEncontrada e
           left join fetch p.responsavel
           left join fetch i.foto
           where i.levantamento.id = :levId
             and (i.resultado in (com.fundacao.gerenciador_patrimonial.domain.enums.ResultadoConferencia.AVARIADO,
                                  com.fundacao.gerenciador_patrimonial.domain.enums.ResultadoConferencia.NAO_LOCALIZADO)
                  or (e is not null and e.id <> l.id))
           order by l.upm, l.nome, p.numeroTombo
           """)
    List<ItemLevantamento> listarOcorrencias(@Param("levId") Long levantamentoId);

    /** Todos os itens para exportação, ordenados por local esperado. */
    @Query("""
           select distinct i
           from ItemLevantamento i
           join fetch i.patrimonio p
           join fetch i.lotacao l
           left join fetch i.lotacaoEncontrada
           left join fetch p.responsavel
           left join fetch i.foto
           where i.levantamento.id = :levId
           order by l.upm, l.nome, p.numeroTombo, p.descricao
           """)
    List<ItemLevantamento> listarTodosParaExportacao(@Param("levId") Long levantamentoId);
}

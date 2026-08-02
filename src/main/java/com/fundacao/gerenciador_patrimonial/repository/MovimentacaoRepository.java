package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Movimentacao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    /**
     * Histórico de um patrimônio. O {@link EntityGraph} carrega as 4 relações
     * LAZY tocadas por {@code MovimentacaoResponse.from} numa única query
     * (sem ele, cada linha disparava até 4 selects).
     */
    @EntityGraph(attributePaths = {
            "lotacaoOrigem", "lotacaoDestino", "responsavelOrigem", "responsavelDestino"})
    List<Movimentacao> findByPatrimonioIdOrderByDataMovimentacaoDesc(Long patrimonioId);

    /** Última movimentação — {@code First} gera LIMIT 1 no SQL. */
    @EntityGraph(attributePaths = {
            "lotacaoOrigem", "lotacaoDestino", "responsavelOrigem", "responsavelDestino"})
    Optional<Movimentacao> findFirstByPatrimonioIdOrderByDataMovimentacaoDesc(Long patrimonioId);

    /** Últimas N movimentações — usado no dashboard. */
    @Query("""
           select m from Movimentacao m
           join fetch m.patrimonio p
           left join fetch m.lotacaoOrigem
           left join fetch m.lotacaoDestino
           left join fetch m.responsavelOrigem
           left join fetch m.responsavelDestino
           order by m.dataMovimentacao desc
           """)
    List<Movimentacao> listarUltimas(Pageable pageable);
}

package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Movimentacao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Long> {

    List<Movimentacao> findByPatrimonioIdOrderByDataMovimentacaoDesc(Long patrimonioId);

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

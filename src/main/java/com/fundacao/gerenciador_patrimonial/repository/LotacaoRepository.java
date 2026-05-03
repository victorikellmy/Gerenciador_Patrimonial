package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LotacaoRepository extends JpaRepository<Lotacao, Long> {

    /** Busca pela chave natural composta (UPM + sala). Usado no importador Excel. */
    Optional<Lotacao> findByUpmAndNome(String upm, String nome);

    /** Listagem simples por UPM. */
    List<Lotacao> findByUpmOrderByNomeAsc(String upm);

    /**
     * Lista completa ordenada para popular {@code <select>} de formulários.
     * Sem paginação: retornar todas é o comportamento esperado no dropdown.
     * Em PostgreSQL, com índice composto em (upm, nome), o sort sai do índice.
     */
    List<Lotacao> findAllByOrderByUpmAscNomeAsc();

    boolean existsByUpmAndNome(String upm, String nome);

    /** UPMs distintas, ordenadas — alimenta dropdowns de filtro. */
    @Query("select distinct l.upm from Lotacao l where l.upm is not null order by l.upm")
    List<String> findDistinctUpms();
}

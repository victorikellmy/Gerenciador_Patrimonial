package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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
    @EntityGraph(attributePaths = "responsavelAtual")
    List<Lotacao> findByUpmOrderByNomeAsc(String upm);

    /**
     * Lista completa ordenada para popular {@code <select>} de formulários.
     * Sem paginação: retornar todas é o comportamento esperado no dropdown.
     * Em PostgreSQL, com índice composto em (upm, nome), o sort sai do índice.
     *
     * <p>O {@link EntityGraph} evita 1 select por lotação quando o mapeamento
     * para DTO acessa {@code responsavelAtual} (LAZY).</p>
     */
    @EntityGraph(attributePaths = "responsavelAtual")
    List<Lotacao> findAllByOrderByUpmAscNomeAsc();

    /** Sobrescrito com fetch do responsável — mesmo motivo do método acima. */
    @Override
    @EntityGraph(attributePaths = "responsavelAtual")
    Page<Lotacao> findAll(Pageable pageable);

    boolean existsByUpmAndNome(String upm, String nome);

    /** UPMs distintas, ordenadas — alimenta dropdowns de filtro. Cache TTL 60s (CacheConfig). */
    @Cacheable("upms-distintas")
    @Query("select distinct l.upm from Lotacao l where l.upm is not null order by l.upm")
    List<String> findDistinctUpms();
}

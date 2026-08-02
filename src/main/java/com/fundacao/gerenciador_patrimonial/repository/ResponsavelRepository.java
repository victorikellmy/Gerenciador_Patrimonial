package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResponsavelRepository extends JpaRepository<Responsavel, Long> {

    Optional<Responsavel> findByNomeCompleto(String nomeCompleto);

    Optional<Responsavel> findByMatricula(String matricula);

    @EntityGraph(attributePaths = "lotacao")
    List<Responsavel> findByLotacaoIdAndAtivoTrue(Long lotacaoId);

    /**
     * Lista ordenada para popular {@code <select>} de formulários.
     * O {@link EntityGraph} evita 1 select por responsável quando o mapeamento
     * para DTO acessa {@code lotacao} (LAZY).
     */
    @EntityGraph(attributePaths = "lotacao")
    List<Responsavel> findAllByOrderByNomeCompletoAsc();

    /** Sobrescrito com fetch da lotação — mesmo motivo do método acima. */
    @Override
    @EntityGraph(attributePaths = "lotacao")
    Page<Responsavel> findAll(Pageable pageable);
}

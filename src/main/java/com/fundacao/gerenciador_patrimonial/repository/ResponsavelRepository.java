package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResponsavelRepository extends JpaRepository<Responsavel, Long> {

    Optional<Responsavel> findByNomeCompleto(String nomeCompleto);

    Optional<Responsavel> findByMatricula(String matricula);

    List<Responsavel> findByLotacaoIdAndAtivoTrue(Long lotacaoId);

    /** Lista ordenada para popular {@code <select>} de formulários. */
    List<Responsavel> findAllByOrderByNomeCompletoAsc();
}

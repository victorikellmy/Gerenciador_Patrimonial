package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.NivelDiaria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NivelDiariaRepository extends JpaRepository<NivelDiaria, Long> {

    List<NivelDiaria> findAllByOrderByAtivoDescIdAsc();

    List<NivelDiaria> findByAtivoTrueOrderByIdAsc();

    Optional<NivelDiaria> findByIdTransportes(Integer idTransportes);
}

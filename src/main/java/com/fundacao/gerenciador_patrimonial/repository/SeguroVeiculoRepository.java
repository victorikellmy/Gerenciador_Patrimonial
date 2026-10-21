package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.SeguroVeiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SeguroVeiculoRepository extends JpaRepository<SeguroVeiculo, Long> {

    @Query("select s from SeguroVeiculo s join fetch s.veiculo where s.id = :id")
    Optional<SeguroVeiculo> findComVeiculo(Long id);

    @Query("select s from SeguroVeiculo s join fetch s.veiculo v where v.id = :veiculoId order by s.fimVigencia desc, s.id desc")
    List<SeguroVeiculo> doVeiculo(Long veiculoId);

    @Query("select s from SeguroVeiculo s join fetch s.veiculo v order by s.fimVigencia desc, s.id desc")
    List<SeguroVeiculo> todos();
}

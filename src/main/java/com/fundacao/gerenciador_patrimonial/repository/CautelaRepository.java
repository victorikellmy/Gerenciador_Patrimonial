package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Cautela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CautelaRepository extends JpaRepository<Cautela, Long>, JpaSpecificationExecutor<Cautela> {

    @Query("select c from Cautela c join fetch c.veiculo where c.id = :id")
    Optional<Cautela> findComVeiculo(Long id);

    Optional<Cautela> findByIdTransportes(Integer idTransportes);

    @Query("select c from Cautela c join fetch c.veiculo where c.kmFinal is null order by c.dataViagem desc, c.id desc")
    List<Cautela> emAberto();

    Optional<Cautela> findFirstByVeiculoIdAndKmFinalIsNullOrderByDataViagemDescIdDesc(Long veiculoId);

    long countByKmFinalIsNull();

    List<Cautela> findByVeiculoIdOrderByDataViagemDescIdDesc(Long veiculoId);
}

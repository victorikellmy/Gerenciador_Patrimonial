package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Deslocamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeslocamentoRepository extends JpaRepository<Deslocamento, Long>, JpaSpecificationExecutor<Deslocamento> {

    List<Deslocamento> findByVeiculoIdOrderByDataViagemDescIdDesc(Long veiculoId);

    @Query("select d from Deslocamento d join fetch d.veiculo where d.id = :id")
    Optional<Deslocamento> findComVeiculo(Long id);

    Optional<Deslocamento> findByIdTransportes(Integer idTransportes);

    /** Saídas ainda sem km final (veículo na rua). */
    @Query("select d from Deslocamento d join fetch d.veiculo where d.kmFinal is null order by d.dataViagem desc, d.id desc")
    List<Deslocamento> emAndamento();

    Optional<Deslocamento> findFirstByVeiculoIdAndKmFinalIsNullOrderByDataViagemDescIdDesc(Long veiculoId);

    Optional<Deslocamento> findFirstByVeiculoIdOrderByDataViagemDescIdDesc(Long veiculoId);

    long countByKmFinalIsNull();

    long countByDataViagemBetween(LocalDate de, LocalDate ate);

    long countByVeiculoId(Long veiculoId);

    @Query("select coalesce(sum(d.kmFinal - d.kmInicial), 0) from Deslocamento d " +
           "where d.veiculo.id = :veiculoId and d.kmFinal is not null and d.kmInicial is not null " +
           "and d.dataViagem between :de and :ate")
    Long kmRodadosVeiculoPeriodo(Long veiculoId, LocalDate de, LocalDate ate);
}

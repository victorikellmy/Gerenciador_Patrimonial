package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Abastecimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AbastecimentoRepository extends JpaRepository<Abastecimento, Long>, JpaSpecificationExecutor<Abastecimento> {

    List<Abastecimento> findByVeiculoIdOrderByDataDescIdDesc(Long veiculoId);

    @Query("select a from Abastecimento a join fetch a.veiculo where a.id = :id")
    Optional<Abastecimento> findComVeiculo(Long id);

    Optional<Abastecimento> findByIdTransportes(Integer idTransportes);

    @Query("select coalesce(sum(a.valor), 0) from Abastecimento a where a.data between :de and :ate")
    BigDecimal totalPeriodo(LocalDate de, LocalDate ate);

    @Query("select coalesce(sum(a.valor), 0) from Abastecimento a where a.veiculo.id = :veiculoId and a.data between :de and :ate")
    BigDecimal totalVeiculoPeriodo(Long veiculoId, LocalDate de, LocalDate ate);

    @Query("select coalesce(sum(a.quantidadeLitros), 0) from Abastecimento a where a.veiculo.id = :veiculoId and a.data between :de and :ate")
    BigDecimal litrosVeiculoPeriodo(Long veiculoId, LocalDate de, LocalDate ate);

    long countByVeiculoId(Long veiculoId);
}

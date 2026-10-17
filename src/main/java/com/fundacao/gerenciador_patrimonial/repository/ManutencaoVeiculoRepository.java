package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.ManutencaoVeiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ManutencaoVeiculoRepository extends JpaRepository<ManutencaoVeiculo, Long>, JpaSpecificationExecutor<ManutencaoVeiculo> {

    List<ManutencaoVeiculo> findByVeiculoIdOrderByDataDescIdDesc(Long veiculoId);

    @Query("select m from ManutencaoVeiculo m join fetch m.veiculo where m.id = :id")
    Optional<ManutencaoVeiculo> findComVeiculo(Long id);

    Optional<ManutencaoVeiculo> findByIdTransportes(Integer idTransportes);

    @Query("select coalesce(sum(m.valor), 0) from ManutencaoVeiculo m where m.data between :de and :ate")
    BigDecimal totalPeriodo(LocalDate de, LocalDate ate);

    @Query("select coalesce(sum(m.valor), 0) from ManutencaoVeiculo m where m.veiculo.id = :veiculoId and m.data between :de and :ate")
    BigDecimal totalVeiculoPeriodo(Long veiculoId, LocalDate de, LocalDate ate);

    long countByDataBetween(LocalDate de, LocalDate ate);

    long countByVeiculoId(Long veiculoId);

    /** Última revisão/manutenção com próxima revisão programada. */
    Optional<ManutencaoVeiculo> findFirstByVeiculoIdAndProximaRevisaoKmNotNullOrderByDataDescIdDesc(Long veiculoId);

    Optional<ManutencaoVeiculo> findFirstByVeiculoIdAndProximaRevisaoDataNotNullOrderByDataDescIdDesc(Long veiculoId);
}

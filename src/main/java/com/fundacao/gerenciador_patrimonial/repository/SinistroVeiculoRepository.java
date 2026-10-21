package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.SinistroVeiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SinistroVeiculoRepository extends JpaRepository<SinistroVeiculo, Long> {

    @Query("select s from SinistroVeiculo s join fetch s.veiculo left join fetch s.seguro where s.id = :id")
    Optional<SinistroVeiculo> findComVeiculo(Long id);

    @Query("select s from SinistroVeiculo s join fetch s.veiculo v left join fetch s.seguro where v.id = :veiculoId order by s.dataOcorrencia desc, s.id desc")
    List<SinistroVeiculo> doVeiculo(Long veiculoId);

    @Query("select s from SinistroVeiculo s join fetch s.veiculo left join fetch s.seguro order by s.dataOcorrencia desc, s.id desc")
    List<SinistroVeiculo> todos();

    boolean existsBySeguroId(Long seguroId);
}

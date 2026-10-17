package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Veiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface VeiculoRepository extends JpaRepository<Veiculo, Long> {

    @Query("select v from Veiculo v join fetch v.patrimonio p order by v.ativo desc, v.placa asc")
    List<Veiculo> listarComPatrimonio();

    List<Veiculo> findByAtivoTrueOrderByPlacaAsc();

    @Query("select v from Veiculo v join fetch v.patrimonio where v.id = :id")
    Optional<Veiculo> findComPatrimonio(Long id);

    Optional<Veiculo> findByPatrimonioId(Long patrimonioId);

    Optional<Veiculo> findByPlacaIgnoreCase(String placa);

    Optional<Veiculo> findByIdTransportes(Integer idTransportes);

    long countByAtivoTrue();
}

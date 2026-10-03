package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.PendenciaPatrimoniamento;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusPendencia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PendenciaPatrimoniamentoRepository extends JpaRepository<PendenciaPatrimoniamento, Long> {

    Optional<PendenciaPatrimoniamento> findByOrigemAndCompraIdOrigem(String origem, Long compraIdOrigem);

    long countByStatus(StatusPendencia status);

    List<PendenciaPatrimoniamento> findByStatusOrderByCriadoEmAsc(StatusPendencia status);

    Page<PendenciaPatrimoniamento> findByStatusOrderByCriadoEmDesc(StatusPendencia status, Pageable pageable);
}

package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.VidaUtilCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VidaUtilCategoriaRepository extends JpaRepository<VidaUtilCategoria, Long> {

    Optional<VidaUtilCategoria> findByCategoriaIgnoreCase(String categoria);
}

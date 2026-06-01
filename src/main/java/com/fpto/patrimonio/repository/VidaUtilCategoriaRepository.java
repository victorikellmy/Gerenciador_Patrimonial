package com.fpto.patrimonio.repository;

import com.fpto.patrimonio.domain.entity.VidaUtilCategoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VidaUtilCategoriaRepository extends JpaRepository<VidaUtilCategoria, Long> {

    Optional<VidaUtilCategoria> findByCategoriaIgnoreCase(String categoria);
}

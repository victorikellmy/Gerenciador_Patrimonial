package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.ArquivoAnexo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArquivoAnexoRepository extends JpaRepository<ArquivoAnexo, Long> {

    List<ArquivoAnexo> findByPatrimonioId(Long patrimonioId);
}

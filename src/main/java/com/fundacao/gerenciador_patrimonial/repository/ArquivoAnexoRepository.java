package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.ArquivoAnexo;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoAnexo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ArquivoAnexoRepository extends JpaRepository<ArquivoAnexo, Long> {

    List<ArquivoAnexo> findByPatrimonioId(Long patrimonioId);

    /** Anexos de um tipo para vários bens (ex.: fotos atuais no checklist do levantamento). */
    List<ArquivoAnexo> findByPatrimonioIdInAndTipoOrderByCriadoEmDesc(Collection<Long> patrimonioIds,
                                                                      TipoAnexo tipo);
}

package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.DiariaHistorico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiariaHistoricoRepository extends JpaRepository<DiariaHistorico, Long> {

    List<DiariaHistorico> findByDiariaIdOrderByInstanteAscIdAsc(Long diariaId);

    void deleteByDiariaId(Long diariaId);
}

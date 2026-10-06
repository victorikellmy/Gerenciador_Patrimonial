package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.LevantamentoPatrimonial;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusLevantamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LevantamentoPatrimonialRepository extends JpaRepository<LevantamentoPatrimonial, Long> {

    /** Só um levantamento fica ABERTO por vez — regra garantida no serviço. */
    Optional<LevantamentoPatrimonial> findFirstByStatus(StatusLevantamento status);

    boolean existsByStatus(StatusLevantamento status);

    List<LevantamentoPatrimonial> findAllByOrderByAbertoEmDesc();
}

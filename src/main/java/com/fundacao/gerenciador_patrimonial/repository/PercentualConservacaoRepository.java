package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.PercentualConservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PercentualConservacaoRepository extends JpaRepository<PercentualConservacao, Long> {

    Optional<PercentualConservacao> findByConservacao(Conservacao conservacao);
}

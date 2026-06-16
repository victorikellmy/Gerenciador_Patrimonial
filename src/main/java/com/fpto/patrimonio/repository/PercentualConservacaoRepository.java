package com.fpto.patrimonio.repository;

import com.fpto.patrimonio.domain.entity.PercentualConservacao;
import com.fpto.patrimonio.domain.enums.Conservacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PercentualConservacaoRepository extends JpaRepository<PercentualConservacao, Long> {

    Optional<PercentualConservacao> findByConservacao(Conservacao conservacao);
}

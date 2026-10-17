package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Diaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiariaRepository extends JpaRepository<Diaria, Long>, JpaSpecificationExecutor<Diaria> {

    @Query("select d from Diaria d join fetch d.profissional p join fetch p.nivel where d.id = :id")
    Optional<Diaria> findComProfissional(Long id);

    Optional<Diaria> findByIdTransportes(Integer idTransportes);

    long countByStatus(StatusDiaria status);

    @Query("select d from Diaria d join fetch d.profissional where d.status = :status order by d.dataSolicitacao asc, d.id asc")
    List<Diaria> pendentes(StatusDiaria status, Pageable limite);

    @Query("select d from Diaria d join fetch d.profissional where d.status = com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria.PAGO " +
           "and d.dataPagamento between :de and :ate order by d.dataPagamento asc, d.id asc")
    List<Diaria> pagasNoPeriodo(LocalDate de, LocalDate ate);

    @Query("select coalesce(sum(d.valorTotal), 0) from Diaria d where d.status = com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria.PAGO " +
           "and d.dataPagamento between :de and :ate")
    BigDecimal totalPagoNoPeriodo(LocalDate de, LocalDate ate);
}

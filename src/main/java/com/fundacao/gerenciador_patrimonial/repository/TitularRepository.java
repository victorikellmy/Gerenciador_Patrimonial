package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Titular;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TitularRepository extends JpaRepository<Titular, Long> {

    @Query("select t.nome from Titular t where lower(t.nome) like :termo order by t.nome")
    List<String> sugerirNomes(String termo, Pageable limite);

    Optional<Titular> findByIdTransportes(Integer idTransportes);
}

package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Profissional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {

    @Query("select p from Profissional p join fetch p.nivel where p.id = :id")
    Optional<Profissional> findComNivel(Long id);

    @Query("select p from Profissional p join fetch p.nivel order by p.ativo desc, p.nome asc")
    List<Profissional> listarComNivel();

    @Query("select p from Profissional p join fetch p.nivel where p.ativo = true order by p.nome asc")
    List<Profissional> ativosComNivel();

    @Query("select p from Profissional p join fetch p.nivel where lower(p.nome) like :termo or p.cpf like :termo or lower(p.matricula) like :termo order by p.ativo desc, p.nome asc")
    List<Profissional> buscar(String termo);

    Optional<Profissional> findByIdTransportes(Integer idTransportes);

    Optional<Profissional> findFirstByCpf(String cpf);
}

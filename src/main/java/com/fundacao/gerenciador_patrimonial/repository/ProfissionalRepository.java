package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.Profissional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {

    @Query("select p from Profissional p left join fetch p.nivel where p.id = :id")
    Optional<Profissional> findComNivel(Long id);

    @Query("select p from Profissional p left join fetch p.nivel order by p.ativo desc, p.nome asc")
    List<Profissional> listarComNivel();

    @Query("select p from Profissional p left join fetch p.nivel where p.ativo = true order by p.nome asc")
    List<Profissional> ativosComNivel();

    @Query("select p from Profissional p left join fetch p.nivel where lower(p.nome) like :termo or p.cpf like :termo or lower(p.matricula) like :termo order by p.ativo desc, p.nome asc")
    List<Profissional> buscar(String termo);

    /**
     * Busca do formulário de diária: nome, matrícula ou CPF (com ou sem pontuação).
     * {@code termo} já vem em minúsculas com %; {@code digitos} só com algarismos e % (ou um
     * texto que nunca casa, quando o termo não tem algarismos suficientes).
     */
    @Query("select p from Profissional p left join fetch p.nivel where p.ativo = true and (" +
           "lower(p.nome) like :termo or lower(p.matricula) like :termo or p.cpf like :termo " +
           "or replace(replace(p.cpf, '.', ''), '-', '') like :digitos) " +
           "order by p.nome asc")
    List<Profissional> buscarParaFormulario(String termo, String digitos, org.springframework.data.domain.Pageable limite);

    Optional<Profissional> findByIdTransportes(Integer idTransportes);

    Optional<Profissional> findFirstByCpf(String cpf);
}

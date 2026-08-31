package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.AuditoriaAcao;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditoriaAcaoRepository extends JpaRepository<AuditoriaAcao, Long> {

    /**
     * Busca paginada com filtros opcionais — todos os parâmetros podem ser nulos.
     * Usar query JPQL explícita evita a complexidade de Specifications para uso só-leitura.
     */
    @Query("""
           select a
           from AuditoriaAcao a
           where (:usuario  is null or lower(a.usuario)  like lower(concat('%', :usuario,  '%')))
             and (:acao     is null or a.acao     = :acao)
             and (:entidade is null or a.entidade = :entidade)
             and (:entidadeId is null or a.entidadeId = :entidadeId)
             and (:de  is null or a.dataHora >= :de)
             and (:ate is null or a.dataHora <= :ate)
           order by a.dataHora desc
           """)
    Page<AuditoriaAcao> buscar(String usuario,
                               AcaoAuditoria acao,
                               String entidade,
                               Long entidadeId,
                               LocalDateTime de,
                               LocalDateTime ate,
                               Pageable pageable);

    /** Top-N usuários por volume de ações — para painel admin. */
    @Query("""
           select a.usuario, count(a)
           from AuditoriaAcao a
           where (:de is null or a.dataHora >= :de)
           group by a.usuario
           order by count(a) desc
           """)
    List<Object[]> contarPorUsuario(LocalDateTime de, Pageable pageable);
}

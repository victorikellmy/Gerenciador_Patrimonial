package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.domain.entity.AuditoriaAcao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A busca filtrada usa Specifications ({@code AuditoriaSpecifications.comFiltro})
 * em vez do antigo padrão {@code (:param is null or ...)} — só os predicados
 * informados entram no SQL, permitindo ao banco usar os índices da tabela.
 */
@Repository
public interface AuditoriaAcaoRepository
        extends JpaRepository<AuditoriaAcao, Long>, JpaSpecificationExecutor<AuditoriaAcao> {

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

package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.dto.response.AuditoriaResponse;
import com.fundacao.gerenciador_patrimonial.repository.AuditoriaAcaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.spec.AuditoriaSpecifications;
import com.fundacao.gerenciador_patrimonial.util.Textos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consulta da trilha de auditoria — lógica compartilhada entre a tela web
 * ({@code AuditoriaWebController}) e a API ({@code AdminAuditoriaController}),
 * que antes duplicavam a mesma busca e o mesmo mapeamento de top usuários.
 */
@Service
@RequiredArgsConstructor
public class AuditoriaConsultaService {

    private final AuditoriaAcaoRepository repo;

    /** Linha do ranking de usuários mais ativos (substitui o Map&lt;String,Object&gt; anterior). */
    public record TopUsuario(String usuario, long total) {}

    @Transactional(readOnly = true)
    public Page<AuditoriaResponse> buscar(String usuario, AcaoAuditoria acao, String entidade,
                                          Long entidadeId, LocalDateTime de, LocalDateTime ate,
                                          Pageable pageable) {
        // Ordenação fixa por data desc (a mais recente primeiro), preservando page/size do chamador.
        Pageable ordenado = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "dataHora"));
        return repo.findAll(
                        AuditoriaSpecifications.comFiltro(
                                Textos.nullIfBlank(usuario), acao, Textos.nullIfBlank(entidade),
                                entidadeId, de, ate),
                        ordenado)
                .map(AuditoriaResponse::from);
    }

    @Transactional(readOnly = true)
    public List<TopUsuario> topUsuarios(LocalDateTime de, int limite) {
        return repo.contarPorUsuario(de, PageRequest.of(0, limite)).stream()
                .map(r -> new TopUsuario((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }
}

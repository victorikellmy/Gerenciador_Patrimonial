package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.diff.DiffPatrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Movimentacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.dto.request.BaixaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroPatrimonio;
import com.fundacao.gerenciador_patrimonial.dto.request.MovimentacaoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.MovimentacaoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.PatrimonioResponse;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.MovimentacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.repository.spec.PatrimonioSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Regras de negócio de Patrimônio: CRUD, filtros, movimentação e baixa.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PatrimonioService {

    private static final String ENT = "Patrimonio";

    private final PatrimonioRepository patrimonioRepository;
    private final LotacaoRepository lotacaoRepository;
    private final ResponsavelRepository responsavelRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final DepreciacaoService depreciacaoService;
    private final AuditoriaService auditoriaService;

    public PatrimonioResponse criar(PatrimonioRequest request) {
        validarTomboUnico(request.numeroTombo(), null);

        Patrimonio salvo = patrimonioRepository.save(Patrimonio.criar(
                request,
                buscarLotacao(request.lotacaoId()),
                buscarResponsavel(request.responsavelId())));

        auditoriaService.registrar(AcaoAuditoria.CREATE, ENT, salvo.getId(),
                "Cadastro: tombo=%s, descrição=%s, categoria=%s"
                        .formatted(salvo.getNumeroTombo(), salvo.getDescricao(), salvo.getCategoria()));
        return toResponse(salvo);
    }

    public PatrimonioResponse atualizar(Long id, PatrimonioRequest request) {
        Patrimonio p = buscar(id);
        validarTomboUnico(request.numeroTombo(), id);

        DiffPatrimonio diff = p.aplicar(
                request,
                buscarLotacao(request.lotacaoId()),
                buscarResponsavel(request.responsavelId()));

        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT, id, diff.descricao());
        return toResponse(p);
    }

    @Transactional(readOnly = true)
    public PatrimonioResponse buscarPorId(Long id) {
        return toResponse(buscar(id));
    }

    @Transactional(readOnly = true)
    public Page<PatrimonioResponse> pesquisar(FiltroPatrimonio filtro, Pageable pageable) {
        return patrimonioRepository
                .findAll(PatrimonioSpecifications.comFiltro(filtro), pageable)
                .map(this::toResponse);
    }

    // =========================================================================
    // Ciclo de vida: movimentação e baixa
    // =========================================================================

    /**
     * Move o patrimônio para outra lotação e/ou responsável.
     * Gera automaticamente um registro em {@code Movimentacao} para auditoria.
     * A regra "baixado não pode ser movimentado" vive na entidade.
     */
    public PatrimonioResponse movimentar(Long id, MovimentacaoRequest request) {
        if (request.novaLotacaoId() == null && request.novoResponsavelId() == null) {
            throw new RegraDeNegocioException(
                    "Informe pelo menos a nova lotação ou o novo responsável.");
        }

        Patrimonio p = buscar(id);
        Lotacao lotacaoOrigem  = p.getLotacao();
        Responsavel respOrigem = p.getResponsavel();

        p.movimentar(
                request.novaLotacaoId()    != null ? buscarLotacao(request.novaLotacaoId())          : null,
                request.novoResponsavelId() != null ? buscarResponsavel(request.novoResponsavelId()) : null);

        Movimentacao mov = Movimentacao.builder()
                .patrimonio(p)
                .lotacaoOrigem(lotacaoOrigem)
                .lotacaoDestino(p.getLotacao())
                .responsavelOrigem(respOrigem)
                .responsavelDestino(p.getResponsavel())
                .observacao(request.observacao())
                .executadoPor(usuarioAtual())
                .build();
        movimentacaoRepository.save(mov);

        auditoriaService.registrar(AcaoAuditoria.MOVIMENTAR, ENT, id,
                "Lotação: %s → %s; Responsável: %s → %s".formatted(
                        lotacaoOrigem != null ? lotacaoOrigem.getNome() : "—",
                        p.getLotacao()  != null ? p.getLotacao().getNome()  : "—",
                        respOrigem      != null ? respOrigem.getNomeCompleto() : "—",
                        p.getResponsavel() != null ? p.getResponsavel().getNomeCompleto() : "—"));

        return toResponse(p);
    }

    /** Baixa lógica (soft delete): o bem permanece no banco com situação BAIXADO. */
    public PatrimonioResponse darBaixa(Long id, BaixaRequest request) {
        Patrimonio p = buscar(id);
        if (p.getSituacao() == SituacaoPatrimonio.BAIXADO) {
            throw new RegraDeNegocioException("Patrimônio já está baixado.");
        }
        p.darBaixa(request.motivo());
        auditoriaService.registrar(AcaoAuditoria.BAIXAR, ENT, id,
                "Baixa: " + request.motivo());
        return toResponse(p);
    }

    /** Exclusão física — apenas quando realmente é ruído/erro de cadastro. */
    public void excluirPermanentemente(Long id) {
        Patrimonio p = buscar(id);
        String resumo = "Exclusão definitiva: tombo=%s, descrição=%s"
                .formatted(p.getNumeroTombo(), p.getDescricao());
        // Move 'histórico' não bloqueia; anexos têm cascade no mapeamento.
        patrimonioRepository.delete(p);
        auditoriaService.registrar(AcaoAuditoria.DELETE, ENT, id, resumo);
    }

    // =========================================================================
    // Histórico de movimentação
    // =========================================================================

    /** Histórico completo de movimentações de um patrimônio (mais recente primeiro). */
    @Transactional(readOnly = true)
    public List<MovimentacaoResponse> historico(Long patrimonioId) {
        // Garante 404 se o patrimônio não existir.
        buscar(patrimonioId);
        return movimentacaoRepository.findByPatrimonioIdOrderByDataMovimentacaoDesc(patrimonioId)
                .stream()
                .map(MovimentacaoResponse::from)
                .toList();
    }

    /** Última movimentação registrada (ou {@code null} se nunca foi movimentado). */
    @Transactional(readOnly = true)
    public MovimentacaoResponse ultimaMovimentacao(Long patrimonioId) {
        return movimentacaoRepository.findByPatrimonioIdOrderByDataMovimentacaoDesc(patrimonioId)
                .stream()
                .findFirst()
                .map(MovimentacaoResponse::from)
                .orElse(null);
    }

    // =========================================================================
    // helpers
    // =========================================================================

    /** Login do usuário autenticado, ou {@code "SYSTEM"} se anônimo. */
    private static String usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "SYSTEM";
        }
        return auth.getName();
    }

    private Patrimonio buscar(Long id) {
        return patrimonioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Patrimônio", id));
    }

    private Lotacao buscarLotacao(Long id) {
        return lotacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lotação", id));
    }

    private Responsavel buscarResponsavel(Long id) {
        return responsavelRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Responsável", id));
    }

    private void validarTomboUnico(String tombo, Long idExistente) {
        String norm = nullIfBlank(tombo);
        if (norm == null) return;
        patrimonioRepository.findByNumeroTombo(norm).ifPresent(p -> {
            if (!p.getId().equals(idExistente)) {
                throw new RegraDeNegocioException(
                        "Número de tombo '%s' já está em uso.".formatted(norm));
            }
        });
    }

    private String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private PatrimonioResponse toResponse(Patrimonio p) {
        return PatrimonioResponse.from(p, depreciacaoService.calcular(p));
    }
}

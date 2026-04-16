package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Movimentacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.dto.request.BaixaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroPatrimonio;
import com.fundacao.gerenciador_patrimonial.dto.request.MovimentacaoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio de Patrimônio: CRUD, filtros, movimentação e baixa.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PatrimonioService {

    private final PatrimonioRepository patrimonioRepository;
    private final LotacaoRepository lotacaoRepository;
    private final ResponsavelRepository responsavelRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final DepreciacaoService depreciacaoService;

    public PatrimonioResponse criar(PatrimonioRequest request) {
        validarTomboUnico(request.numeroTombo(), null);

        Patrimonio p = Patrimonio.builder()
                .numeroTombo(nullIfBlank(request.numeroTombo()))
                .descricao(request.descricao().trim())
                .categoria(request.categoria())
                .dataCompra(request.dataCompra())
                .valorCompra(request.valorCompra())
                .conservacao(request.conservacao())
                .notaFiscal(request.notaFiscal())
                .situacao(SituacaoPatrimonio.ATIVO)
                .lotacao(buscarLotacao(request.lotacaoId()))
                .responsavel(buscarResponsavel(request.responsavelId()))
                .build();

        return toResponse(patrimonioRepository.save(p));
    }

    public PatrimonioResponse atualizar(Long id, PatrimonioRequest request) {
        Patrimonio p = buscar(id);
        validarTomboUnico(request.numeroTombo(), id);

        p.setNumeroTombo(nullIfBlank(request.numeroTombo()));
        p.setDescricao(request.descricao().trim());
        p.setCategoria(request.categoria());
        p.setDataCompra(request.dataCompra());
        p.setValorCompra(request.valorCompra());
        p.setConservacao(request.conservacao());
        p.setNotaFiscal(request.notaFiscal());
        p.setLotacao(buscarLotacao(request.lotacaoId()));
        p.setResponsavel(buscarResponsavel(request.responsavelId()));

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
     */
    public PatrimonioResponse movimentar(Long id, MovimentacaoRequest request) {
        if (request.novaLotacaoId() == null && request.novoResponsavelId() == null) {
            throw new RegraDeNegocioException(
                    "Informe pelo menos a nova lotação ou o novo responsável.");
        }

        Patrimonio p = buscar(id);
        if (p.getSituacao() == SituacaoPatrimonio.BAIXADO) {
            throw new RegraDeNegocioException("Patrimônio baixado não pode ser movimentado.");
        }

        Lotacao lotacaoOrigem  = p.getLotacao();
        Responsavel respOrigem = p.getResponsavel();

        if (request.novaLotacaoId() != null) {
            p.setLotacao(buscarLotacao(request.novaLotacaoId()));
        }
        if (request.novoResponsavelId() != null) {
            p.setResponsavel(buscarResponsavel(request.novoResponsavelId()));
        }

        // Trilha de auditoria
        Movimentacao mov = Movimentacao.builder()
                .patrimonio(p)
                .lotacaoOrigem(lotacaoOrigem)
                .lotacaoDestino(p.getLotacao())
                .responsavelOrigem(respOrigem)
                .responsavelDestino(p.getResponsavel())
                .observacao(request.observacao())
                .build();
        movimentacaoRepository.save(mov);

        return toResponse(p);
    }

    /** Baixa lógica (soft delete): o bem permanece no banco com situação BAIXADO. */
    public PatrimonioResponse darBaixa(Long id, BaixaRequest request) {
        Patrimonio p = buscar(id);
        if (p.getSituacao() == SituacaoPatrimonio.BAIXADO) {
            throw new RegraDeNegocioException("Patrimônio já está baixado.");
        }
        p.darBaixa(request.motivo());
        return toResponse(p);
    }

    /** Exclusão física — apenas quando realmente é ruído/erro de cadastro. */
    public void excluirPermanentemente(Long id) {
        Patrimonio p = buscar(id);
        // Move 'histórico' não bloqueia; anexos têm cascade no mapeamento.
        patrimonioRepository.delete(p);
    }

    // =========================================================================
    // helpers
    // =========================================================================

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

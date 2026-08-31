package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.dto.request.LotacaoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.LotacaoResponse;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Regras de negócio para Lotação.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LotacaoService {

    private final LotacaoRepository lotacaoRepository;
    private final ResponsavelRepository responsavelRepository;

    public LotacaoResponse criar(LotacaoRequest request) {
        String upm  = request.upm().trim();
        String nome = request.nome().trim();

        if (lotacaoRepository.existsByUpmAndNome(upm, nome)) {
            throw new RegraDeNegocioException(
                    "Já existe lotação com UPM '%s' e nome '%s'.".formatted(upm, nome));
        }

        Lotacao lotacao = Lotacao.builder()
                .upm(upm)
                .nome(nome)
                .cidade(request.cidade())
                .tipoLocal(request.tipoLocal())
                .responsavelAtual(buscarResponsavelOpcional(request.responsavelAtualId()))
                .build();

        return LotacaoResponse.from(lotacaoRepository.save(lotacao));
    }

    public LotacaoResponse atualizar(Long id, LotacaoRequest request) {
        Lotacao lotacao = buscarEntidade(id);

        lotacao.setUpm(request.upm().trim());
        lotacao.setNome(request.nome().trim());
        lotacao.setCidade(request.cidade());
        lotacao.setTipoLocal(request.tipoLocal());
        lotacao.setResponsavelAtual(buscarResponsavelOpcional(request.responsavelAtualId()));

        return LotacaoResponse.from(lotacao); // dirty checking do JPA persiste
    }

    @Transactional(readOnly = true)
    public Page<LotacaoResponse> listar(Pageable pageable) {
        return lotacaoRepository.findAll(pageable).map(LotacaoResponse::from);
    }

    /** Lista completa para popular dropdowns — substitui o anti-padrão {@code PageRequest.of(0, 1000)}. */
    @Transactional(readOnly = true)
    public List<LotacaoResponse> listarParaSelect() {
        return lotacaoRepository.findAllByOrderByUpmAscNomeAsc().stream()
                .map(LotacaoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LotacaoResponse buscarPorId(Long id) {
        return LotacaoResponse.from(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    public List<LotacaoResponse> buscarPorUpm(String upm) {
        return lotacaoRepository.findByUpmOrderByNomeAsc(upm).stream()
                .map(LotacaoResponse::from)
                .toList();
    }

    public void excluir(Long id) {
        Lotacao lotacao = buscarEntidade(id);
        if (!lotacao.getPatrimonios().isEmpty()) {
            throw new RegraDeNegocioException(
                    "Não é possível excluir a lotação: existem patrimônios vinculados. Movimente-os antes.");
        }
        lotacaoRepository.delete(lotacao);
    }

    /**
     * Substitui o responsável atual de uma lotação inteira.
     * Útil quando há troca de comando/gestão.
     * (A propagação para os patrimônios individuais será implementada
     *  na Sprint 4, em {@code MovimentacaoService}.)
     */
    public LotacaoResponse trocarResponsavelDoSetor(Long lotacaoId, Long novoResponsavelId) {
        Lotacao lotacao = buscarEntidade(lotacaoId);
        Responsavel novo = responsavelRepository.findById(novoResponsavelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Responsável", novoResponsavelId));
        lotacao.setResponsavelAtual(novo);
        return LotacaoResponse.from(lotacao);
    }

    // ------- helpers -------

    private Lotacao buscarEntidade(Long id) {
        return lotacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lotação", id));
    }

    private Responsavel buscarResponsavelOpcional(Long id) {
        if (id == null) return null;
        return responsavelRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Responsável", id));
    }
}

package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.dto.request.LotacaoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.LotacaoResponse;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
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

    private static final String ENT = "Lotacao";

    private final LotacaoRepository lotacaoRepository;
    private final ResponsavelRepository responsavelRepository;
    private final PatrimonioRepository patrimonioRepository;
    private final AuditoriaService auditoriaService;

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

        Lotacao salvo = lotacaoRepository.save(lotacao);
        auditoriaService.registrar(AcaoAuditoria.CREATE, ENT, salvo.getId(),
                "Cadastro: UPM=%s, nome=%s, cidade=%s".formatted(upm, nome, request.cidade()));
        return LotacaoResponse.from(salvo);
    }

    public LotacaoResponse atualizar(Long id, LotacaoRequest request) {
        Lotacao lotacao = buscarEntidade(id);

        String upmAntes  = lotacao.getUpm();
        String nomeAntes = lotacao.getNome();
        String cidadeAntes = lotacao.getCidade();
        Long respAntes = lotacao.getResponsavelAtual() != null ? lotacao.getResponsavelAtual().getId() : null;

        lotacao.setUpm(request.upm().trim());
        lotacao.setNome(request.nome().trim());
        lotacao.setCidade(request.cidade());
        lotacao.setTipoLocal(request.tipoLocal());
        lotacao.setResponsavelAtual(buscarResponsavelOpcional(request.responsavelAtualId()));

        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT, id,
                "UPM: %s → %s; Nome: %s → %s; Cidade: %s → %s; Responsável: %s → %s".formatted(
                        upmAntes, lotacao.getUpm(),
                        nomeAntes, lotacao.getNome(),
                        cidadeAntes, lotacao.getCidade(),
                        respAntes, request.responsavelAtualId()));
        return LotacaoResponse.from(lotacao);
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
        // exists O(1) no banco — o antigo getPatrimonios().isEmpty() hidratava a coleção inteira.
        if (patrimonioRepository.existsByLotacaoId(id)) {
            throw new RegraDeNegocioException(
                    "Não é possível excluir a lotação: existem patrimônios vinculados. Movimente-os antes.");
        }
        String resumo = "Exclusão: UPM=%s, nome=%s".formatted(lotacao.getUpm(), lotacao.getNome());
        lotacaoRepository.delete(lotacao);
        auditoriaService.registrar(AcaoAuditoria.DELETE, ENT, id, resumo);
    }

    /**
     * Substitui o responsável atual de uma lotação inteira.
     * Útil quando há troca de comando/gestão.
     * (A propagação para os patrimônios individuais será implementada
     *  na Sprint 4, em {@code MovimentacaoService}.)
     */
    public LotacaoResponse trocarResponsavelDoSetor(Long lotacaoId, Long novoResponsavelId) {
        Lotacao lotacao = buscarEntidade(lotacaoId);
        Long antes = lotacao.getResponsavelAtual() != null ? lotacao.getResponsavelAtual().getId() : null;
        Responsavel novo = responsavelRepository.findById(novoResponsavelId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Responsável", novoResponsavelId));
        lotacao.setResponsavelAtual(novo);
        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT, lotacaoId,
                "Troca de responsável do setor: %s → %s".formatted(antes, novoResponsavelId));
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

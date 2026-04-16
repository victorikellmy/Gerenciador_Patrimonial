package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.dto.request.ResponsavelRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.ResponsavelResponse;
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

@Service
@RequiredArgsConstructor
@Transactional
public class ResponsavelService {

    private final ResponsavelRepository responsavelRepository;
    private final LotacaoRepository lotacaoRepository;

    public ResponsavelResponse criar(ResponsavelRequest request) {
        validarMatriculaUnica(request.matricula(), null);

        Responsavel responsavel = Responsavel.builder()
                .nomeCompleto(request.nomeCompleto().trim())
                .matricula(normalizarMatricula(request.matricula()))
                .cidade(request.cidade())
                .lotacao(buscarLotacaoOpcional(request.lotacaoId()))
                .ativo(true)
                .build();

        return ResponsavelResponse.from(responsavelRepository.save(responsavel));
    }

    public ResponsavelResponse atualizar(Long id, ResponsavelRequest request) {
        Responsavel responsavel = buscarEntidade(id);
        validarMatriculaUnica(request.matricula(), id);

        responsavel.setNomeCompleto(request.nomeCompleto().trim());
        responsavel.setMatricula(normalizarMatricula(request.matricula()));
        responsavel.setCidade(request.cidade());
        responsavel.setLotacao(buscarLotacaoOpcional(request.lotacaoId()));

        return ResponsavelResponse.from(responsavel);
    }

    @Transactional(readOnly = true)
    public Page<ResponsavelResponse> listar(Pageable pageable) {
        return responsavelRepository.findAll(pageable).map(ResponsavelResponse::from);
    }

    @Transactional(readOnly = true)
    public ResponsavelResponse buscarPorId(Long id) {
        return ResponsavelResponse.from(buscarEntidade(id));
    }

    @Transactional(readOnly = true)
    public List<ResponsavelResponse> buscarPorLotacao(Long lotacaoId) {
        return responsavelRepository.findByLotacaoIdAndAtivoTrue(lotacaoId).stream()
                .map(ResponsavelResponse::from)
                .toList();
    }

    /**
     * Inativa o responsável (soft delete).
     * Preferível a excluir fisicamente, pois pode haver patrimônios referenciando-o.
     */
    public void inativar(Long id) {
        Responsavel r = buscarEntidade(id);
        r.setAtivo(false);
    }

    // ------- helpers -------

    private Responsavel buscarEntidade(Long id) {
        return responsavelRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Responsável", id));
    }

    private Lotacao buscarLotacaoOpcional(Long id) {
        if (id == null) return null;
        return lotacaoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lotação", id));
    }

    private void validarMatriculaUnica(String matricula, Long idExistente) {
        String norm = normalizarMatricula(matricula);
        if (norm == null) return;
        responsavelRepository.findByMatricula(norm).ifPresent(r -> {
            if (!r.getId().equals(idExistente)) {
                throw new RegraDeNegocioException(
                        "Matrícula '%s' já está em uso.".formatted(norm));
            }
        });
    }

    private String normalizarMatricula(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

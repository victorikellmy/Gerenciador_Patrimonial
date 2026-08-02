package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.dto.request.LotacaoRequest;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras de negócio de Lotação: unicidade (upm, nome) e proteção de exclusão. */
class LotacaoServiceTest {

    private LotacaoRepository lotacaoRepo;
    private ResponsavelRepository responsavelRepo;
    private PatrimonioRepository patrimonioRepo;

    private LotacaoService service;

    @BeforeEach
    void setUp() {
        lotacaoRepo     = mock(LotacaoRepository.class);
        responsavelRepo = mock(ResponsavelRepository.class);
        patrimonioRepo  = mock(PatrimonioRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);

        service = new LotacaoService(lotacaoRepo, responsavelRepo, patrimonioRepo, auditoria);
    }

    @Test
    @DisplayName("criar: (UPM, nome) duplicado → RegraDeNegocioException")
    void criarDuplicada() {
        when(lotacaoRepo.existsByUpmAndNome("1 BPM", "ADM")).thenReturn(true);

        var req = new LotacaoRequest("1 BPM", "ADM", "Palmas", TipoLocal.INTERNO, null);
        assertThatThrownBy(() -> service.criar(req))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(lotacaoRepo, never()).save(any());
    }

    @Test
    @DisplayName("criar: normaliza com trim e persiste")
    void criarComSucesso() {
        when(lotacaoRepo.existsByUpmAndNome("1 BPM", "ADM")).thenReturn(false);
        when(lotacaoRepo.save(any(Lotacao.class))).thenAnswer(inv -> {
            Lotacao l = inv.getArgument(0);
            l.setId(10L);
            return l;
        });

        var resp = service.criar(new LotacaoRequest(" 1 BPM ", " ADM ", "Palmas", TipoLocal.INTERNO, null));

        assertThat(resp.id()).isEqualTo(10L);
        assertThat(resp.upm()).isEqualTo("1 BPM");
        assertThat(resp.nome()).isEqualTo("ADM");
    }

    @Test
    @DisplayName("excluir: com patrimônios vinculados → bloqueia sem deletar")
    void excluirComVinculos() {
        Lotacao l = Lotacao.builder().id(3L).upm("1 BPM").nome("ADM").build();
        when(lotacaoRepo.findById(3L)).thenReturn(Optional.of(l));
        when(patrimonioRepo.existsByLotacaoId(3L)).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(3L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("vinculados");
        verify(lotacaoRepo, never()).delete(any());
    }

    @Test
    @DisplayName("excluir: sem vínculos deleta normalmente")
    void excluirSemVinculos() {
        Lotacao l = Lotacao.builder().id(3L).upm("1 BPM").nome("ADM").build();
        when(lotacaoRepo.findById(3L)).thenReturn(Optional.of(l));
        when(patrimonioRepo.existsByLotacaoId(3L)).thenReturn(false);

        service.excluir(3L);

        verify(lotacaoRepo).delete(l);
    }

    @Test
    @DisplayName("trocarResponsavelDoSetor: responsável inexistente → 404")
    void trocarResponsavelInexistente() {
        Lotacao l = Lotacao.builder().id(3L).upm("1 BPM").nome("ADM").build();
        when(lotacaoRepo.findById(3L)).thenReturn(Optional.of(l));
        when(responsavelRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.trocarResponsavelDoSetor(3L, 99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("trocarResponsavelDoSetor: atualiza o responsável atual da lotação")
    void trocarResponsavel() {
        Lotacao l = Lotacao.builder().id(3L).upm("1 BPM").nome("ADM").build();
        Responsavel novo = Responsavel.builder().id(7L).nomeCompleto("Maria").build();
        when(lotacaoRepo.findById(3L)).thenReturn(Optional.of(l));
        when(responsavelRepo.findById(7L)).thenReturn(Optional.of(novo));

        var resp = service.trocarResponsavelDoSetor(3L, 7L);

        assertThat(l.getResponsavelAtual()).isSameAs(novo);
        assertThat(resp.responsavelAtualNome()).isEqualTo("Maria");
    }
}

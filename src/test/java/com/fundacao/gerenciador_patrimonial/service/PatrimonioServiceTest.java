package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.dto.request.BaixaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.MovimentacaoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.MovimentacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regras de negócio de Patrimônio: tombo único, movimentação, baixa e histórico. */
class PatrimonioServiceTest {

    private PatrimonioRepository patrimonioRepo;
    private LotacaoRepository lotacaoRepo;
    private ResponsavelRepository responsavelRepo;
    private MovimentacaoRepository movimentacaoRepo;
    private AuditoriaService auditoriaService;

    private PatrimonioService service;

    private Lotacao lotacao;
    private Responsavel responsavel;

    @BeforeEach
    void setUp() {
        patrimonioRepo   = mock(PatrimonioRepository.class);
        lotacaoRepo      = mock(LotacaoRepository.class);
        responsavelRepo  = mock(ResponsavelRepository.class);
        movimentacaoRepo = mock(MovimentacaoRepository.class);
        auditoriaService = mock(AuditoriaService.class);
        DepreciacaoService depreciacaoService = mock(DepreciacaoService.class);
        when(depreciacaoService.calcular(any(Patrimonio.class)))
                .thenReturn(DepreciacaoService.CalculoDepreciacao.vazio());

        service = new PatrimonioService(patrimonioRepo, lotacaoRepo, responsavelRepo,
                movimentacaoRepo, depreciacaoService, auditoriaService);

        lotacao = Lotacao.builder().id(1L).upm("1 BPM").nome("ADMINISTRACAO").build();
        responsavel = Responsavel.builder().id(2L).nomeCompleto("João da Silva").build();
    }

    private PatrimonioRequest request(String tombo) {
        return new PatrimonioRequest(tombo, "Notebook Dell", "COMPUTADOR", null,
                LocalDate.of(2024, 1, 10), new BigDecimal("3500.00"), null,
                null, null, null, null, null, 1L, 2L);
    }

    // =========================================================================
    // Criação e tombo único
    // =========================================================================

    @Test
    @DisplayName("criar: persiste, audita e devolve DTO com lotação/responsável")
    void criarComSucesso() {
        when(patrimonioRepo.findByNumeroTombo("T-9")).thenReturn(Optional.empty());
        when(lotacaoRepo.findById(1L)).thenReturn(Optional.of(lotacao));
        when(responsavelRepo.findById(2L)).thenReturn(Optional.of(responsavel));
        when(patrimonioRepo.save(any(Patrimonio.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.criar(request("T-9"));

        assertThat(resp.numeroTombo()).isEqualTo("T-9");
        assertThat(resp.situacao()).isEqualTo(SituacaoPatrimonio.ATIVO);
        assertThat(resp.lotacaoId()).isEqualTo(1L);
        assertThat(resp.responsavelNome()).isEqualTo("João da Silva");
        verify(auditoriaService).registrar(eq(AcaoAuditoria.CREATE), eq("Patrimonio"), any(), anyString());
    }

    @Test
    @DisplayName("criar: tombo já usado por outro registro → RegraDeNegocioException")
    void criarComTomboDuplicado() {
        Patrimonio existente = Patrimonio.builder().id(99L).descricao("Outro").build();
        when(patrimonioRepo.findByNumeroTombo("T-9")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.criar(request("T-9")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("T-9");
        verify(patrimonioRepo, never()).save(any());
    }

    // =========================================================================
    // Movimentação
    // =========================================================================

    @Test
    @DisplayName("movimentar: sem nova lotação E sem novo responsável → erro antes de tocar o banco")
    void movimentarSemDestino() {
        assertThatThrownBy(() -> service.movimentar(1L, new MovimentacaoRequest(null, null, null)))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(patrimonioRepo, never()).findById(anyLong());
    }

    @Test
    @DisplayName("movimentar: patrimônio baixado não pode ser movimentado")
    void movimentarBaixado() {
        Patrimonio baixado = Patrimonio.builder()
                .id(5L).descricao("Mesa").situacao(SituacaoPatrimonio.BAIXADO)
                .lotacao(lotacao).responsavel(responsavel)
                .build();
        when(patrimonioRepo.findById(5L)).thenReturn(Optional.of(baixado));
        when(lotacaoRepo.findById(1L)).thenReturn(Optional.of(lotacao));

        assertThatThrownBy(() -> service.movimentar(5L, new MovimentacaoRequest(1L, null, null)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("baixado");
        verify(movimentacaoRepo, never()).save(any());
    }

    @Test
    @DisplayName("movimentar: registra Movimentacao com origem e destino")
    void movimentarComSucesso() {
        Lotacao destino = Lotacao.builder().id(3L).upm("2 BPM").nome("SAUDE").build();
        Patrimonio p = Patrimonio.builder()
                .id(5L).descricao("Mesa").situacao(SituacaoPatrimonio.ATIVO)
                .lotacao(lotacao).responsavel(responsavel)
                .build();
        when(patrimonioRepo.findById(5L)).thenReturn(Optional.of(p));
        when(lotacaoRepo.findById(3L)).thenReturn(Optional.of(destino));

        var resp = service.movimentar(5L, new MovimentacaoRequest(3L, null, "troca de sala"));

        assertThat(resp.lotacaoId()).isEqualTo(3L);
        assertThat(resp.responsavelId()).isEqualTo(2L); // responsável mantido
        verify(movimentacaoRepo).save(any());
        verify(auditoriaService).registrar(eq(AcaoAuditoria.MOVIMENTAR), eq("Patrimonio"), eq(5L), anyString());
    }

    // =========================================================================
    // Baixa
    // =========================================================================

    @Test
    @DisplayName("darBaixa: muda situação e registra motivo")
    void darBaixa() {
        Patrimonio p = Patrimonio.builder()
                .id(7L).descricao("Impressora").situacao(SituacaoPatrimonio.ATIVO)
                .lotacao(lotacao).responsavel(responsavel)
                .build();
        when(patrimonioRepo.findById(7L)).thenReturn(Optional.of(p));

        var resp = service.darBaixa(7L, new BaixaRequest("dano irreparável"));

        assertThat(resp.situacao()).isEqualTo(SituacaoPatrimonio.BAIXADO);
        assertThat(resp.motivoBaixa()).isEqualTo("dano irreparável");
        assertThat(resp.dataBaixa()).isNotNull();
    }

    @Test
    @DisplayName("darBaixa: patrimônio já baixado → RegraDeNegocioException")
    void darBaixaDuplicada() {
        Patrimonio p = Patrimonio.builder()
                .id(7L).descricao("Impressora").situacao(SituacaoPatrimonio.BAIXADO)
                .lotacao(lotacao).responsavel(responsavel)
                .build();
        when(patrimonioRepo.findById(7L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.darBaixa(7L, new BaixaRequest("de novo")))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    // =========================================================================
    // Histórico
    // =========================================================================

    @Test
    @DisplayName("historico: patrimônio inexistente → 404 sem hidratar a entidade")
    void historicoInexistente() {
        when(patrimonioRepo.existsById(123L)).thenReturn(false);

        assertThatThrownBy(() -> service.historico(123L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(patrimonioRepo, never()).findById(anyLong());
        verify(movimentacaoRepo, never()).findByPatrimonioIdOrderByDataMovimentacaoDesc(anyLong());
    }

    @Test
    @DisplayName("ultimaMovimentacao: sem registros → null")
    void ultimaMovimentacaoVazia() {
        when(movimentacaoRepo.findFirstByPatrimonioIdOrderByDataMovimentacaoDesc(1L))
                .thenReturn(Optional.empty());
        assertThat(service.ultimaMovimentacao(1L)).isNull();
    }
}

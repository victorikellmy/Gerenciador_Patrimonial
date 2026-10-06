package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.ResultadoConferencia;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Linha do checklist de um levantamento: um bem a ser conferido em um local.
 *
 * <p>{@link #lotacao} é o local <b>esperado</b> (onde o bem estava cadastrado
 * quando o levantamento foi aberto). Se o bem for encontrado em outra sala,
 * {@link #lotacaoEncontrada} recebe esse local e o item passa a aparecer no
 * checklist das duas lotações, marcado como divergência.</p>
 */
@Entity
@Table(name = "item_levantamento",
       uniqueConstraints = @UniqueConstraint(name = "uk_item_levantamento",
                                             columnNames = {"levantamento_id", "patrimonio_id"}),
       indexes = {
           @Index(name = "idx_item_lev_levantamento", columnList = "levantamento_id"),
           @Index(name = "idx_item_lev_lotacao",      columnList = "levantamento_id, lotacao_id"),
           @Index(name = "idx_item_lev_encontrada",   columnList = "levantamento_id, lotacao_encontrada_id"),
           @Index(name = "idx_item_lev_resultado",    columnList = "levantamento_id, resultado")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ItemLevantamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "levantamento_id", nullable = false)
    private LevantamentoPatrimonial levantamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patrimonio_id", nullable = false)
    private Patrimonio patrimonio;

    /** Local esperado — lotação do bem no momento da abertura. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lotacao_id", nullable = false)
    private Lotacao lotacao;

    /** Local onde o bem foi efetivamente encontrado, quando diferente do esperado. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lotacao_encontrada_id")
    private Lotacao lotacaoEncontrada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ResultadoConferencia resultado = ResultadoConferencia.PENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "conservacao_anterior", length = 30)
    private Conservacao conservacaoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "conservacao_verificada", length = 30)
    private Conservacao conservacaoVerificada;

    @Column(name = "descricao_avaria", length = 1000)
    private String descricaoAvaria;

    @Column(length = 1000)
    private String observacao;

    /** Foto tirada durante a conferência (também fica anexada ao bem como FOTO). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "foto_id")
    private ArquivoAnexo foto;

    @Column(name = "verificado_por", length = 80)
    private String verificadoPor;

    @Column(name = "verificado_em")
    private LocalDateTime verificadoEm;

    // ------------------------------------------------------------------ regras

    public boolean isPendente() { return resultado == ResultadoConferencia.PENDENTE; }

    /** Bem encontrado em local diferente do esperado. */
    public boolean isLocalDivergente() {
        return lotacaoEncontrada != null && lotacao != null
                && !lotacaoEncontrada.getId().equals(lotacao.getId());
    }

    /**
     * Registra o resultado da conferência física.
     *
     * @param novoResultado   OK, AVARIADO ou NAO_LOCALIZADO (PENDENTE não é aceito aqui)
     * @param conservacao     conservação observada (opcional; {@code null} mantém a atual)
     * @param avaria          descrição da avaria — obrigatória quando AVARIADO
     * @param obs             observação livre
     * @param localEncontrado lotação em que o bem foi achado ({@code null} = no local esperado)
     * @param usuario         login de quem conferiu
     */
    public void conferir(ResultadoConferencia novoResultado,
                         Conservacao conservacao,
                         String avaria,
                         String obs,
                         Lotacao localEncontrado,
                         String usuario) {
        if (novoResultado == null || novoResultado == ResultadoConferencia.PENDENTE) {
            throw new RegraDeNegocioException("Informe o resultado da conferência (OK, Avariado ou Não localizado).");
        }
        String avariaNorm = nullIfBlank(avaria);
        if (novoResultado == ResultadoConferencia.AVARIADO && avariaNorm == null) {
            throw new RegraDeNegocioException("Descreva a avaria encontrada no bem.");
        }
        if (novoResultado == ResultadoConferencia.NAO_LOCALIZADO) {
            // Quem não foi localizado não tem "local encontrado".
            localEncontrado = null;
            avariaNorm = null;
        }

        this.resultado             = novoResultado;
        this.conservacaoVerificada = conservacao;
        this.descricaoAvaria       = avariaNorm;
        this.observacao            = nullIfBlank(obs);
        this.lotacaoEncontrada     = localEncontrado;
        this.verificadoPor         = usuario;
        this.verificadoEm          = LocalDateTime.now();
    }

    /** Volta o item para PENDENTE (erro de registro). A foto já anexada ao bem é mantida. */
    public void desfazerConferencia() {
        this.resultado             = ResultadoConferencia.PENDENTE;
        this.conservacaoVerificada = null;
        this.descricaoAvaria       = null;
        this.observacao            = null;
        this.lotacaoEncontrada     = null;
        this.foto                  = null;
        this.verificadoPor         = null;
        this.verificadoEm          = null;
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

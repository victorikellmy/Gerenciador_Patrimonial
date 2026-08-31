package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.diff.DiffPatrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Patrimônio — agregado raiz do domínio.
 *
 * <p>Campos <b>derivados</b> (valor depreciado, VUD, VUR, etc.) <b>não são persistidos</b>:
 * são calculados sob demanda pelo serviço de depreciação a partir de
 * {@link #valorCompra}, {@link #categoria} e {@link #conservacao}.</p>
 */
@Entity
@Table(name = "patrimonio", indexes = {
        @Index(name = "idx_patrimonio_situacao", columnList = "situacao"),
        @Index(name = "idx_patrimonio_lotacao", columnList = "lotacao_id"),
        @Index(name = "idx_patrimonio_resp", columnList = "responsavel_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Patrimonio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número de tombo — chave de negócio (único quando informado). */
    @Column(name = "numero_tombo", length = 30, unique = true)
    private String numeroTombo;

    @Column(nullable = false, length = 255)
    private String descricao;

    /** Categoria: COMPUTADOR, EQUIPAMENTO, IMPRESSORA, MOVEIS. */
    @Column(length = 60)
    private String categoria;

    /** Subcategoria — refinamento abaixo de categoria (Computadores, Mesas, Cadeiras, Ar condicionado). */
    @Column(length = 60)
    private String subcategoria;

    /** Data de aquisição (pode ser nula — maior parte da planilha original não tem). */
    @Column(name = "data_compra")
    private LocalDate dataCompra;

    @Column(name = "valor_compra", precision = 19, scale = 2)
    private BigDecimal valorCompra;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Conservacao conservacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SituacaoPatrimonio situacao = SituacaoPatrimonio.ATIVO;

    @Column(name = "nota_fiscal", length = 60)
    private String notaFiscal;

    /** Valor recuperável estimado (entrada do laudo de impairment). */
    @Column(name = "valor_recuperavel", precision = 19, scale = 2)
    private BigDecimal valorRecuperavel;

    /** Texto-conclusão do teste de impairment (ex.: "NÃO HÁ PERDA..."). */
    @Column(name = "conclusao_impairment", length = 255)
    private String conclusaoImpairment;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    /** URL de referência de mercado usada para estimar valor recuperável. */
    @Column(name = "link_referencia", length = 2000)
    private String linkReferencia;

    // --- Soft delete (baixa) ---
    @Column(name = "data_baixa")
    private LocalDate dataBaixa;

    @Column(name = "motivo_baixa", length = 255)
    private String motivoBaixa;

    // --- Relacionamentos ---
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lotacao_id", nullable = false)
    private Lotacao lotacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private Responsavel responsavel;

    @OneToMany(mappedBy = "patrimonio", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ArquivoAnexo> anexos = new ArrayList<>();

    @OneToMany(mappedBy = "patrimonio", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Movimentacao> historicoMovimentacoes = new ArrayList<>();

    // --- Auditoria ---
    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @CreatedBy
    @Column(name = "criado_por", length = 80, updatable = false)
    private String criadoPor;

    @LastModifiedBy
    @Column(name = "atualizado_por", length = 80)
    private String atualizadoPor;

    /**
     * Executa a baixa lógica (soft delete).
     * Mantém o registro no banco para rastreabilidade.
     */
    public void darBaixa(String motivo) {
        this.situacao   = SituacaoPatrimonio.BAIXADO;
        this.dataBaixa  = LocalDate.now();
        this.motivoBaixa = motivo;
    }

    /**
     * Move o patrimônio para outra lotação e/ou responsável.
     * Argumentos {@code null} mantêm o valor atual — útil quando só
     * um dos dois muda. Invariante: bens baixados não podem ser movidos.
     */
    public void movimentar(Lotacao novaLotacao, Responsavel novoResponsavel) {
        if (situacao == SituacaoPatrimonio.BAIXADO) {
            throw new RegraDeNegocioException("Patrimônio baixado não pode ser movimentado.");
        }
        if (novaLotacao != null)    this.lotacao = novaLotacao;
        if (novoResponsavel != null) this.responsavel = novoResponsavel;
    }

    /**
     * Factory para novos cadastros — fixa {@code situacao = ATIVO} e delega a
     * cópia dos campos para {@link #aplicar}. O diff retornado é descartado
     * porque um novo registro não tem estado anterior para comparar.
     */
    public static Patrimonio criar(PatrimonioRequest req,
                                   Lotacao lotacao,
                                   Responsavel responsavel) {
        Patrimonio p = new Patrimonio();
        p.situacao = SituacaoPatrimonio.ATIVO;
        p.aplicar(req, lotacao, responsavel);
        return p;
    }

    /**
     * Copia os campos editáveis do request para a entidade e devolve o
     * {@link DiffPatrimonio} com as mudanças efetivadas. Único ponto de
     * normalização (trim, nullIfBlank) — para que criação e edição sigam
     * exatamente as mesmas regras.
     *
     * <p>Campos governados pelo ciclo de vida ({@code situacao},
     * {@code dataBaixa}, {@code motivoBaixa}) são intencionalmente
     * deixados de fora — ver {@link #darBaixa}.</p>
     */
    public DiffPatrimonio aplicar(PatrimonioRequest req,
                                  Lotacao novaLotacao,
                                  Responsavel novoResponsavel) {
        String tomboNovo     = nullIfBlank(req.numeroTombo());
        String descricaoNova = req.descricao().trim();
        Long lotacaoIdAntes  = lotacao     != null ? lotacao.getId()     : null;
        Long respIdAntes     = responsavel != null ? responsavel.getId() : null;
        Long lotacaoIdNovo   = novaLotacao    != null ? novaLotacao.getId()    : null;
        Long respIdNovo      = novoResponsavel != null ? novoResponsavel.getId() : null;

        DiffPatrimonio diff = DiffPatrimonio.builder()
                .compara("tombo",               this.numeroTombo,         tomboNovo)
                .compara("descricao",           this.descricao,           descricaoNova)
                .compara("categoria",           this.categoria,           req.categoria())
                .compara("subcategoria",        this.subcategoria,        req.subcategoria())
                .compara("dataCompra",          this.dataCompra,          req.dataCompra())
                .compara("valorCompra",         this.valorCompra,         req.valorCompra())
                .compara("conservacao",         this.conservacao,         req.conservacao())
                .compara("notaFiscal",          this.notaFiscal,          req.notaFiscal())
                .compara("valorRecuperavel",    this.valorRecuperavel,    req.valorRecuperavel())
                .compara("conclusaoImpairment", this.conclusaoImpairment, req.conclusaoImpairment())
                .compara("observacao",          this.observacao,          req.observacao())
                .compara("linkReferencia",      this.linkReferencia,      req.linkReferencia())
                .compara("lotacaoId",           lotacaoIdAntes,           lotacaoIdNovo)
                .compara("responsavelId",       respIdAntes,              respIdNovo)
                .build();

        this.numeroTombo         = tomboNovo;
        this.descricao           = descricaoNova;
        this.categoria           = req.categoria();
        this.subcategoria        = req.subcategoria();
        this.dataCompra          = req.dataCompra();
        this.valorCompra         = req.valorCompra();
        this.conservacao         = req.conservacao();
        this.notaFiscal          = req.notaFiscal();
        this.valorRecuperavel    = req.valorRecuperavel();
        this.conclusaoImpairment = req.conclusaoImpairment();
        this.observacao          = req.observacao();
        this.linkReferencia      = req.linkReferencia();
        this.lotacao             = novaLotacao;
        this.responsavel         = novoResponsavel;

        return diff;
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

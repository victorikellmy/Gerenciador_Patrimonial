package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoPatrimonio;
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
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
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
}

package com.fundacao.gerenciador_patrimonial.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Apólice de seguro de um veículo da frota. Cada renovação é uma apólice nova
 * (o histórico fica); a vigente é a que cobre a data de hoje.
 */
@Entity
@Table(name = "frota_seguro", indexes = {
        @Index(name = "idx_seguro_veiculo", columnList = "veiculo_id"),
        @Index(name = "idx_seguro_fim",     columnList = "fim_vigencia")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SeguroVeiculo {

    /** Dias antes do fim da vigência em que o seguro passa a "vencendo". */
    public static final int DIAS_AVISO_VENCIMENTO = 30;

    public enum Situacao { VIGENTE, VENCENDO, VENCIDO, FUTURO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @Column(nullable = false, length = 120)
    private String seguradora;

    @Column(name = "numero_apolice", nullable = false, length = 60)
    private String numeroApolice;

    @Column(length = 120)
    private String corretor;

    @Column(name = "inicio_vigencia", nullable = false)
    private LocalDate inicioVigencia;

    @Column(name = "fim_vigencia", nullable = false)
    private LocalDate fimVigencia;

    @Column(name = "valor_premio", precision = 12, scale = 2)
    private BigDecimal valorPremio;

    @Column(precision = 12, scale = 2)
    private BigDecimal franquia;

    @Column(length = 1000)
    private String coberturas;

    /** Assistência 24h / guincho, para o motorista acionar. */
    @Column(name = "telefone_assistencia", length = 40)
    private String telefoneAssistencia;

    @Column(length = 500)
    private String observacao;

    @Column(name = "apolice_nome_original", length = 255)
    private String apoliceNomeOriginal;

    @Column(name = "apolice_content_type", length = 100)
    private String apoliceContentType;

    @Column(name = "apolice_tamanho_bytes")
    private Long apoliceTamanhoBytes;

    @Column(name = "apolice_caminho", length = 500)
    private String apoliceCaminho;

    @Column(name = "registrado_por", length = 80)
    private String registradoPor;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    public boolean possuiApolice() { return apoliceCaminho != null && !apoliceCaminho.isBlank(); }

    public Situacao situacao(LocalDate hoje) {
        if (inicioVigencia.isAfter(hoje)) return Situacao.FUTURO;
        if (fimVigencia.isBefore(hoje)) return Situacao.VENCIDO;
        if (!fimVigencia.isAfter(hoje.plusDays(DIAS_AVISO_VENCIMENTO))) return Situacao.VENCENDO;
        return Situacao.VIGENTE;
    }

    /** Cobre a data (vigente ou vencendo). */
    public boolean cobre(LocalDate data) {
        return !inicioVigencia.isAfter(data) && !fimVigencia.isBefore(data);
    }
}

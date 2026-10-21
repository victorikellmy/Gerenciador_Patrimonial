package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoSinistro;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Ocorrência acionada no seguro (colisão, roubo, vidro...), ligada à apólice que a cobriu. */
@Entity
@Table(name = "frota_sinistro", indexes = {
        @Index(name = "idx_sinistro_veiculo", columnList = "veiculo_id"),
        @Index(name = "idx_sinistro_seguro",  columnList = "seguro_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SinistroVeiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    /** Apólice acionada; nula quando o veículo não tinha seguro vigente na data. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seguro_id")
    private SeguroVeiculo seguro;

    @Column(name = "data_ocorrencia", nullable = false)
    private LocalDate dataOcorrencia;

    /** Protocolo do sinistro na seguradora. */
    @Column(name = "numero_sinistro", length = 60)
    private String numeroSinistro;

    @Column(nullable = false, length = 1000)
    private String descricao;

    @Column(name = "local_ocorrencia", length = 160)
    private String localOcorrencia;

    @Column(length = 160)
    private String condutor;

    @Column(name = "valor_prejuizo", precision = 12, scale = 2)
    private BigDecimal valorPrejuizo;

    @Column(name = "valor_indenizado", precision = 12, scale = 2)
    private BigDecimal valorIndenizado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoSinistro situacao;

    @Column(length = 500)
    private String observacao;

    @Column(name = "registrado_por", length = 80)
    private String registradoPor;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;
}

package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.TipoServicoVeiculo;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Manutenção ou revisão de um veículo da frota. */
@Entity
@Table(name = "frota_manutencao", indexes = {
        @Index(name = "idx_manutencao_veiculo", columnList = "veiculo_id"),
        @Index(name = "idx_manutencao_data",    columnList = "data")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ManutencaoVeiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoServicoVeiculo servico;

    private Integer km;

    @Column(length = 1000)
    private String descricao;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(length = 160)
    private String fornecedor;

    @Column(name = "nota_fiscal", length = 60)
    private String notaFiscal;

    @Column(name = "proxima_revisao_km")
    private Integer proximaRevisaoKm;

    @Column(name = "proxima_revisao_data")
    private LocalDate proximaRevisaoData;

    @Column(nullable = false, length = 160)
    private String motorista;

    @Column(name = "registrado_por", length = 80)
    private String registradoPor;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "id_transportes")
    private Integer idTransportes;
}

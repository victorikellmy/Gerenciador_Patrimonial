package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.Combustivel;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Abastecimento de um veículo da frota. */
@Entity
@Table(name = "frota_abastecimento", indexes = {
        @Index(name = "idx_abastecimento_veiculo", columnList = "veiculo_id"),
        @Index(name = "idx_abastecimento_data",    columnList = "data")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Abastecimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @Column(nullable = false)
    private LocalDate data;

    /** Odômetro no momento do abastecimento. */
    private Integer km;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Combustivel combustivel;

    @Column(name = "quantidade_litros", precision = 10, scale = 3)
    private BigDecimal quantidadeLitros;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "posto_cidade", length = 160)
    private String postoCidade;

    @Column(nullable = false, length = 160)
    private String motorista;

    @Column(length = 500)
    private String observacao;

    /** Login de quem lançou no sistema. */
    @Column(name = "registrado_por", length = 80)
    private String registradoPor;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "id_transportes")
    private Integer idTransportes;

    // --- Nota fiscal (PDF/foto) no StorageService ---
    @Column(name = "nf_nome_original", length = 255)
    private String nfNomeOriginal;

    @Column(name = "nf_content_type", length = 100)
    private String nfContentType;

    @Column(name = "nf_tamanho_bytes")
    private Long nfTamanhoBytes;

    @Column(name = "nf_caminho", length = 500)
    private String nfCaminho;

    public boolean possuiNotaFiscal() { return nfCaminho != null && !nfCaminho.isBlank(); }

    public BigDecimal getPrecoLitro() {
        if (valor == null || quantidadeLitros == null || quantidadeLitros.signum() == 0) return null;
        return valor.divide(quantidadeLitros, 3, RoundingMode.HALF_UP);
    }
}

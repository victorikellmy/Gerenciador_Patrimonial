package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.Combustivel;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Veículo da frota. É sempre um {@link Patrimonio} (categoria VEICULO) com os
 * dados específicos de automóvel. Abastecimentos, manutenções e deslocamentos
 * apontam para cá e formam o histórico do carro.
 */
@Entity
@Table(name = "veiculo",
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_veiculo_patrimonio",  columnNames = "patrimonio_id"),
           @UniqueConstraint(name = "uk_veiculo_transportes", columnNames = "id_transportes")
       },
       indexes = {
           @Index(name = "idx_veiculo_placa", columnList = "placa"),
           @Index(name = "idx_veiculo_ativo", columnList = "ativo")
       })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Veiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patrimonio_id", nullable = false)
    private Patrimonio patrimonio;

    @Column(nullable = false, length = 20)
    private String placa;

    @Column(nullable = false, length = 60)
    private String modelo;

    @Column(length = 60)
    private String marca;

    @Column(length = 50)
    private String chassi;

    @Column(length = 20)
    private String renavam;

    @Column(length = 10)
    private String ano;

    @Column(length = 60)
    private String cidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "combustivel_padrao", length = 20)
    private Combustivel combustivelPadrao;

    /** Maior odômetro já registrado em qualquer lançamento. */
    @Column(name = "km_atual")
    private Integer kmAtual;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    /** Chave no banco do Transportes FPTO (importação idempotente). */
    @Column(name = "id_transportes")
    private Integer idTransportes;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    /** "QKJ-7909 - GOL - PALMAS", como o select do sistema antigo mostrava. */
    public String getRotulo() {
        StringBuilder sb = new StringBuilder(placa).append(" - ").append(modelo);
        if (cidade != null && !cidade.isBlank()) sb.append(" - ").append(cidade);
        return sb.toString();
    }

    /** Avança o odômetro quando um lançamento traz km maior que o conhecido. */
    public void atualizarKm(Integer km) {
        if (km == null) return;
        if (kmAtual == null || km > kmAtual) kmAtual = km;
    }
}

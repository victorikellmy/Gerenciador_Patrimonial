package com.fundacao.gerenciador_patrimonial.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Saída (locomoção) de um veículo da frota: destino, motivo, horários e odômetro. */
@Entity
@Table(name = "frota_deslocamento", indexes = {
        @Index(name = "idx_deslocamento_veiculo", columnList = "veiculo_id"),
        @Index(name = "idx_deslocamento_data",    columnList = "data_viagem"),
        @Index(name = "idx_deslocamento_aberto",  columnList = "km_final")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Deslocamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @Column(name = "data_viagem", nullable = false)
    private LocalDate dataViagem;

    @Column(nullable = false, length = 160)
    private String destino;

    @Column(nullable = false, length = 60)
    private String motivo;

    /** Militar/servidor atendido. */
    @Column(length = 160)
    private String titular;

    @Column(length = 160)
    private String assistido;

    @Column(name = "hora_saida")
    private LocalTime horaSaida;

    @Column(name = "km_inicial")
    private Integer kmInicial;

    @Column(name = "hora_chegada")
    private LocalTime horaChegada;

    /** Nulo enquanto o veículo estiver na rua. */
    @Column(name = "km_final")
    private Integer kmFinal;

    @Column(nullable = false, length = 160)
    private String motorista;

    @Column(length = 60)
    private String lotacao;

    @Column(length = 500)
    private String observacao;

    @Column(name = "registrado_por", length = 80)
    private String registradoPor;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "id_transportes")
    private Integer idTransportes;

    public boolean isEmAndamento() { return kmFinal == null; }

    public Integer getKmRodados() {
        if (kmInicial == null || kmFinal == null) return null;
        return kmFinal - kmInicial;
    }
}

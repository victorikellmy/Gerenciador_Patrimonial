package com.fundacao.gerenciador_patrimonial.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Cautela: veículo entregue a um condutor externo (militar de outra unidade,
 * por exemplo) com termo de responsabilidade. Guarda os dados da viagem e a
 * identificação de quem assume o veículo.
 */
@Entity
@Table(name = "frota_cautela", indexes = {
        @Index(name = "idx_cautela_veiculo", columnList = "veiculo_id"),
        @Index(name = "idx_cautela_data",    columnList = "data_viagem"),
        @Index(name = "idx_cautela_aberta",  columnList = "km_final")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Cautela {

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

    @Column(length = 160)
    private String responsavel;

    @Column(length = 160)
    private String assistido;

    @Column(name = "hora_saida")
    private LocalTime horaSaida;

    @Column(name = "km_inicial")
    private Integer kmInicial;

    @Column(name = "hora_chegada")
    private LocalTime horaChegada;

    @Column(name = "km_final")
    private Integer kmFinal;

    /** Condutor que recebe o veículo (não é o usuário logado). */
    @Column(name = "motorista_nome", nullable = false, length = 160)
    private String motoristaNome;

    @Column(length = 30)
    private String rg;

    @Column(length = 14)
    private String cpf;

    @Column(length = 25)
    private String fone;

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

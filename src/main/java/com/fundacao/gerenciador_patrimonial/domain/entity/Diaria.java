package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.MeioTransporte;
import com.fundacao.gerenciador_patrimonial.domain.enums.RecursoDiaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Solicitação de pagamento de diária(s) de viagem. */
@Entity
@Table(name = "diaria", indexes = {
        @Index(name = "idx_diaria_status",    columnList = "status"),
        @Index(name = "idx_diaria_pagamento", columnList = "data_pagamento"),
        @Index(name = "idx_diaria_partida",   columnList = "partida")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Diaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_solicitacao", nullable = false)
    private LocalDate dataSolicitacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Column(nullable = false, length = 200)
    private String itinerario;

    @Column(name = "hora_saida")
    private LocalTime horaSaida;

    @Enumerated(EnumType.STRING)
    @Column(name = "meio_transporte", nullable = false, length = 20)
    private MeioTransporte meioTransporte;

    @Column(nullable = false)
    private LocalDate partida;

    @Column(nullable = false)
    private LocalDate retorno;

    @Column(name = "hora_chegada")
    private LocalTime horaChegada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecursoDiaria recurso;

    @Column(name = "numero_diarias", nullable = false, precision = 6, scale = 1)
    private BigDecimal numeroDiarias;

    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(length = 2000)
    private String finalidade;

    /** Nome de quem abriu a solicitação. */
    @Column(nullable = false, length = 160)
    private String solicitante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusDiaria status = StatusDiaria.SOLICITADO;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Column(name = "motivo_indeferimento", length = 500)
    private String motivoIndeferimento;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @Column(name = "id_transportes")
    private Integer idTransportes;

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) criadoEm = LocalDateTime.now();
        if (dataSolicitacao == null) dataSolicitacao = LocalDate.now();
    }

    @PreUpdate
    void aoAtualizar() { atualizadoEm = LocalDateTime.now(); }

    public boolean isEditavel() { return status.isEditavel(); }
}

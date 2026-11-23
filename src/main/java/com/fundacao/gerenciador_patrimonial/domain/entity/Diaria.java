package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.MeioTransporte;
import com.fundacao.gerenciador_patrimonial.domain.enums.RecursoDiaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoDiaria;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Requerimento de diária(s) de viagem (Anexo II da Portaria 001/2019 GAB. PRES).
 *
 * <p>Guarda uma "fotografia" dos dados do profissional e da conta bancária no momento do pedido
 * ({@code prof*}, banco, agência, conta, PIX): o documento emitido não muda se o cadastro for
 * alterado depois. Diárias anteriores à V14 podem ter esses campos vazios.</p>
 */
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

    /** Número do requerimento ("12/2026"): gerado em sequência por ano ou informado pelo usuário. */
    @Column(name = "numero_requerimento", length = 20)
    private String numeroRequerimento;

    @Column(name = "data_solicitacao", nullable = false)
    private LocalDate dataSolicitacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    // --- fotografia do profissional no momento do pedido ---
    @Column(name = "prof_nome", length = 160)
    private String profNome;

    @Column(name = "prof_matricula", length = 30)
    private String profMatricula;

    @Column(name = "prof_cargo", length = 160)
    private String profCargo;

    @Column(name = "prof_lotacao", length = 60)
    private String profLotacao;

    @Column(name = "prof_cpf", length = 14)
    private String profCpf;

    @Column(name = "prof_rg", length = 30)
    private String profRg;

    @Column(name = "prof_orgao_expedidor", length = 30)
    private String profOrgaoExpedidor;

    @Column(length = 3)
    private String banco;

    @Column(length = 15)
    private String agencia;

    @Column(name = "conta_corrente", length = 20)
    private String contaCorrente;

    @Column(length = 120)
    private String pix;

    /** Texto livre do cadastro antigo ("BB ag 1234 cc 5678"); só nas diárias anteriores à V14. */
    @Column(name = "dados_bancarios", length = 160)
    private String dadosBancarios;

    @Column(length = 120)
    private String origem;

    /** Destino / itinerário. */
    @Column(nullable = false, length = 200)
    private String itinerario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_diaria", length = 30)
    private TipoDiaria tipoDiaria;

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

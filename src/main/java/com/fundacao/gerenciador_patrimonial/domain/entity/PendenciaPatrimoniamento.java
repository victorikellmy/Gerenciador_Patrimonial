package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.StatusPendencia;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pendência de patrimoniamento recebida de um sistema externo (Almoxarifado).
 *
 * <p>Guarda uma cópia dos dados do recebimento (documento de origem, fornecedor,
 * NF, setor de destino, itens) para que o operador consiga cadastrar o bem sem
 * consultar o outro sistema. O par {@code (origem, compraIdOrigem)} é único e
 * garante idempotência dos reenvios da fila do Almoxarifado.</p>
 */
@Entity
@Table(name = "pendencia_patrimoniamento",
       uniqueConstraints = @UniqueConstraint(name = "uk_pendencia_origem",
                                             columnNames = {"origem", "compra_id_origem"}),
       indexes = {
           @Index(name = "idx_pendencia_status",    columnList = "status"),
           @Index(name = "idx_pendencia_criado_em", columnList = "criado_em")
       })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PendenciaPatrimoniamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String origem;

    @Column(name = "compra_id_origem", nullable = false)
    private Long compraIdOrigem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusPendencia status = StatusPendencia.PENDENTE;

    @Column(name = "numero_documento", length = 30)
    private String numeroDocumento;

    @Column(name = "numero_sgd", length = 40)
    private String numeroSgd;

    @Column(length = 255)
    private String assunto;

    @Column(name = "solicitante_documento", length = 255)
    private String solicitanteDocumento;

    @Column(length = 150)
    private String fornecedor;

    @Column(name = "numero_nota_fiscal", length = 60)
    private String numeroNotaFiscal;

    @Column(name = "data_recebimento")
    private LocalDateTime dataRecebimento;

    @Column(name = "data_compra")
    private LocalDate dataCompra;

    @Column(name = "valor_total", precision = 19, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "retirado_por", length = 120)
    private String retiradoPor;

    @Column(name = "setor_destino", length = 150)
    private String setorDestino;

    @Column(name = "setor_destino_cc", length = 30)
    private String setorDestinoCentroCusto;

    @Column(name = "registrado_por", length = 120)
    private String registradoPor;

    @Column(length = 1000)
    private String observacao;

    /** Itens exatamente como recebidos (JSON). Interpretação fica no serviço. */
    @Column(name = "itens_json", length = ITENS_JSON_MAX)
    private String itensJson;

    /** Tamanho da coluna {@code itens_json} (VARCHAR) — validado no serviço antes de salvar. */
    public static final int ITENS_JSON_MAX = 10000;

    @Column(name = "quantidade_total", nullable = false)
    @Builder.Default
    private int quantidadeTotal = 1;

    @Column(name = "quantidade_patrimoniada", nullable = false)
    @Builder.Default
    private int quantidadePatrimoniada = 0;

    // --- Nota fiscal (PDF) guardada no StorageService ---
    @Column(name = "nf_nome_original", length = 255)
    private String nfNomeOriginal;

    @Column(name = "nf_content_type", length = 100)
    private String nfContentType;

    @Column(name = "nf_tamanho_bytes")
    private Long nfTamanhoBytes;

    @Column(name = "nf_caminho", length = 500)
    private String nfCaminho;

    /** Último patrimônio criado a partir desta pendência. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patrimonio_id")
    private Patrimonio patrimonio;

    @Column(name = "concluida_por", length = 80)
    private String concluidaPor;

    @Column(name = "concluida_em")
    private LocalDateTime concluidaEm;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    // ------------------------------------------------------------------ regras

    public boolean isPendente() { return status == StatusPendencia.PENDENTE; }

    public int getQuantidadeRestante() {
        return Math.max(0, quantidadeTotal - quantidadePatrimoniada);
    }

    public boolean possuiNotaFiscal() { return nfCaminho != null && !nfCaminho.isBlank(); }

    /**
     * Registra que um bem foi cadastrado a partir desta pendência. Quando a
     * quantidade restante zera, a pendência é concluída automaticamente.
     */
    public void registrarPatrimoniado(Patrimonio bem, String usuario) {
        this.patrimonio = bem;
        this.quantidadePatrimoniada++;
        if (getQuantidadeRestante() == 0) {
            concluir(usuario);
        }
    }

    public void concluir(String usuario) {
        this.status = StatusPendencia.CONCLUIDA;
        this.concluidaPor = usuario;
        this.concluidaEm = LocalDateTime.now();
    }

    public void descartar(String usuario) {
        this.status = StatusPendencia.DESCARTADA;
        this.concluidaPor = usuario;
        this.concluidaEm = LocalDateTime.now();
    }
}

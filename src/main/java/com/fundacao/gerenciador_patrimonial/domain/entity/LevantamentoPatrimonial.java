package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.StatusLevantamento;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Levantamento patrimonial — campanha (normalmente anual) de conferência física
 * dos bens, local a local.
 *
 * <p>Na abertura, cada bem não baixado vira um {@link ItemLevantamento} com a
 * lotação em que estava naquele momento. A conferência acontece pelo guia de
 * locais; ao final o levantamento é concluído e os itens não conferidos ficam
 * registrados como pendentes.</p>
 */
@Entity
@Table(name = "levantamento_patrimonial", indexes = {
        @Index(name = "idx_levantamento_status", columnList = "status"),
        @Index(name = "idx_levantamento_ano",    columnList = "ano")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class LevantamentoPatrimonial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int ano;

    @Column(nullable = false, length = 150)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusLevantamento status = StatusLevantamento.ABERTO;

    @Column(length = 1000)
    private String observacao;

    /** Quantidade de bens incluídos na abertura (não muda depois). */
    @Column(name = "total_itens", nullable = false)
    @Builder.Default
    private int totalItens = 0;

    @CreatedBy
    @Column(name = "aberto_por", length = 80, updatable = false)
    private String abertoPor;

    @CreatedDate
    @Column(name = "aberto_em", nullable = false, updatable = false)
    private LocalDateTime abertoEm;

    @Column(name = "concluido_por", length = 80)
    private String concluidoPor;

    @Column(name = "concluido_em")
    private LocalDateTime concluidoEm;

    // ------------------------------------------------------------------ regras

    public boolean isAberto() { return status == StatusLevantamento.ABERTO; }

    /** Garante que o levantamento ainda aceita conferências. */
    public void exigirAberto() {
        if (!isAberto()) {
            throw new RegraDeNegocioException(
                    "Levantamento '%s' já foi concluído e não aceita alterações.".formatted(descricao));
        }
    }

    public void concluir(String usuario) {
        exigirAberto();
        this.status = StatusLevantamento.CONCLUIDO;
        this.concluidoPor = usuario;
        this.concluidoEm = LocalDateTime.now();
    }

    public void reabrir() {
        if (isAberto()) {
            throw new RegraDeNegocioException("Levantamento já está aberto.");
        }
        this.status = StatusLevantamento.ABERTO;
        this.concluidoPor = null;
        this.concluidoEm = null;
    }
}

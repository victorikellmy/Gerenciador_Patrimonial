package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.TipoAnexo;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Anexo de patrimônio (PDF de nota fiscal, foto do bem, manual, etc.).
 *
 * <p><b>Abordagem:</b> o arquivo é salvo em disco (ou nuvem, futuramente)
 * e somente o <i>caminho</i> fica no banco. O bean {@code StorageService}
 * abstrai LOCAL/S3.</p>
 */
@Entity
@Table(name = "arquivo_anexo", indexes = {
        @Index(name = "idx_anexo_patrimonio", columnList = "patrimonio_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ArquivoAnexo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patrimonio_id", nullable = false)
    private Patrimonio patrimonio;

    @Column(name = "nome_original", nullable = false, length = 255)
    private String nomeOriginal;

    /** Caminho relativo/URI no storage (ex.: {@code patrimonio/2026/04/uuid.pdf}). */
    @Column(name = "caminho_armazenamento", nullable = false, length = 500)
    private String caminhoArmazenamento;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAnexo tipo;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;
}

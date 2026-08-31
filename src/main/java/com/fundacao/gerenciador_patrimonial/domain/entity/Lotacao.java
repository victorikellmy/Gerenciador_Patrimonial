package com.fundacao.gerenciador_patrimonial.domain.entity;

import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lotação — local onde o patrimônio está alocado.
 *
 * <p>A chave de negócio é a combinação única <b>(upm, nome)</b>, pois a mesma
 * "sala" pode existir em várias UPMs (ex.: "ADMINISTRACAO" no 1 BPM e no 4 BPM).</p>
 */
@Entity
@Table(
        name = "lotacao",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_lotacao_upm_nome",
                columnNames = {"upm", "nome"}
        )
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Lotacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UPM normalizada (ex.: "1 BPM", "2 BPM ARAGUAINA"). */
    @Column(nullable = false, length = 120)
    private String upm;

    /** Nome do setor/sala (ex.: "CONS. ODONTO", "ADMINISTRACAO"). */
    @Column(nullable = false, length = 120)
    private String nome;

    /** Cidade (quando aplicável, para locais externos). */
    @Column(length = 80)
    private String cidade;

    /** Tipo do local (INTERNO ou EXTERNO). */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_local", nullable = false, length = 20)
    private TipoLocal tipoLocal;

    /**
     * Responsável atual do setor inteiro. Permite a operação de
     * "trocar o comandante/gestor", alterando os bens do setor em lote.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_atual_id", foreignKey = @ForeignKey(name = "fk_lotacao_responsavel"))
    private Responsavel responsavelAtual;

    @OneToMany(mappedBy = "lotacao", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Patrimonio> patrimonios = new ArrayList<>();

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @LastModifiedDate
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}

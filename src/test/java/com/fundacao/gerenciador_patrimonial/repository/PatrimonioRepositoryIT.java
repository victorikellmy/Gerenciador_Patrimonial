package com.fundacao.gerenciador_patrimonial.repository;

import com.fundacao.gerenciador_patrimonial.config.JpaAuditingConfig;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Movimentacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração dos repositórios contra o H2 do perfil dev
 * (mesmas migrations Flyway de produção).
 *
 * <p>Foco: garantir que os {@code @EntityGraph} adicionados na otimização
 * realmente carregam as relações LAZY na mesma query — se alguém remover
 * a anotação, os asserts de {@code Hibernate.isInitialized} quebram.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Banco H2 exclusivo deste teste: o mem:patrimonialdb do perfil dev é
// compartilhado pela JVM e o smoke test da aplicação roda o seed nele.
@org.springframework.test.context.TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:patrimonialdb-it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Import(JpaAuditingConfig.class)
class PatrimonioRepositoryIT {

    /** O @EnableJpaAuditing referencia o bean pelo nome "auditorAwareImpl". */
    @TestConfiguration
    static class AuditingTestConfig {
        @Bean
        AuditorAware<String> auditorAwareImpl() {
            return () -> Optional.of("TEST");
        }
    }

    @Autowired private PatrimonioRepository patrimonioRepo;
    @Autowired private LotacaoRepository lotacaoRepo;
    @Autowired private ResponsavelRepository responsavelRepo;
    @Autowired private MovimentacaoRepository movimentacaoRepo;
    @Autowired private TestEntityManager em;

    private Lotacao lotacao;
    private Responsavel responsavel;
    private Patrimonio patrimonio;

    @BeforeEach
    void seed() {
        responsavel = em.persist(Responsavel.builder()
                .nomeCompleto("João da Silva").ativo(true).build());
        lotacao = em.persist(Lotacao.builder()
                .upm("1 BPM").nome("ADM").tipoLocal(TipoLocal.INTERNO)
                .responsavelAtual(responsavel).build());
        patrimonio = em.persist(Patrimonio.builder()
                .numeroTombo("T-100").descricao("Notebook").categoria("COMPUTADOR")
                .valorCompra(new BigDecimal("3500.00"))
                .lotacao(lotacao).responsavel(responsavel).build());
        em.persist(Patrimonio.builder()
                .descricao("Sem tombo").lotacao(lotacao).responsavel(responsavel).build());

        em.persist(Movimentacao.builder()
                .patrimonio(patrimonio).lotacaoOrigem(lotacao).responsavelDestino(responsavel)
                .dataMovimentacao(LocalDateTime.of(2026, 1, 1, 10, 0)).build());
        em.persist(Movimentacao.builder()
                .patrimonio(patrimonio).lotacaoDestino(lotacao)
                .dataMovimentacao(LocalDateTime.of(2026, 6, 1, 10, 0)).build());

        // Limpa o persistence context: sem isso, as entidades já estariam na
        // sessão e Hibernate.isInitialized passaria mesmo sem o EntityGraph.
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findAll(spec, pageable) carrega lotação e responsável na mesma query (EntityGraph)")
    void pesquisaPaginadaSemN1() {
        Page<Patrimonio> pagina = patrimonioRepo.findAll(
                (Specification<Patrimonio>) null, PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        for (Patrimonio p : pagina) {
            assertThat(Hibernate.isInitialized(p.getLotacao())).isTrue();
            assertThat(Hibernate.isInitialized(p.getResponsavel())).isTrue();
        }
    }

    @Test
    @DisplayName("listagens de Lotação/Responsável trazem a relação do DTO já inicializada")
    void listagensDropdownSemN1() {
        var lotacoes = lotacaoRepo.findAllByOrderByUpmAscNomeAsc();
        assertThat(lotacoes).isNotEmpty();
        assertThat(Hibernate.isInitialized(lotacoes.get(0).getResponsavelAtual())).isTrue();

        var responsaveis = responsavelRepo.findAllByOrderByNomeCompletoAsc();
        assertThat(responsaveis).isNotEmpty();
        // lotacao do responsável é null neste seed — só valida que a query executa
        // com o graph sem erro; o caso não-nulo é coberto pela lotação acima.
    }

    @Test
    @DisplayName("findFirst... devolve a movimentação mais recente com relações inicializadas")
    void ultimaMovimentacao() {
        var ultima = movimentacaoRepo
                .findFirstByPatrimonioIdOrderByDataMovimentacaoDesc(patrimonio.getId());

        assertThat(ultima).isPresent();
        assertThat(ultima.get().getDataMovimentacao())
                .isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 0));
        assertThat(Hibernate.isInitialized(ultima.get().getLotacaoDestino())).isTrue();
    }

    @Test
    @DisplayName("existsByLotacaoId e findAllNumerosTombo (checks O(1) e pré-carga da importação)")
    void checksEProjecoes() {
        assertThat(patrimonioRepo.existsByLotacaoId(lotacao.getId())).isTrue();
        assertThat(patrimonioRepo.existsByLotacaoId(99999L)).isFalse();

        // Apenas tombos não-nulos entram na pré-carga de deduplicação.
        assertThat(patrimonioRepo.findAllNumerosTombo()).containsExactly("T-100");
    }
}

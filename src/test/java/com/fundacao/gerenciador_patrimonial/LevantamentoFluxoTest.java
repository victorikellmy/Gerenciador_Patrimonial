package com.fundacao.gerenciador_patrimonial;

import com.fundacao.gerenciador_patrimonial.domain.entity.ItemLevantamento;
import com.fundacao.gerenciador_patrimonial.domain.entity.LevantamentoPatrimonial;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.domain.enums.ResultadoConferencia;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusLevantamento;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoAnexo;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.repository.ArquivoAnexoRepository;
import com.fundacao.gerenciador_patrimonial.repository.ItemLevantamentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.LevantamentoPatrimonialRepository;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Levantamento patrimonial pela API usada pela SPA: abrir → guia por local → conferir
 * (OK / avaria com foto / não localizado) → localizar bem de outra sala → regularizar →
 * exportar → concluir.
 */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha=",
        "app.frota.transportes.url=",
        "app.frota.seed=false"
})
@AutoConfigureMockMvc
class LevantamentoFluxoTest {

    private MockMvc mvc;
    private UsuarioAutenticado fiscal;

    @Autowired WebApplicationContext ctx;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired LevantamentoPatrimonialRepository levantamentoRepo;
    @Autowired ItemLevantamentoRepository itemRepo;
    @Autowired PatrimonioRepository patrimonioRepo;
    @Autowired ArquivoAnexoRepository anexoRepo;
    @Autowired LotacaoRepository lotacaoRepo;
    @Autowired ResponsavelRepository responsavelRepo;

    private Lotacao salaA;
    private Lotacao salaB;
    private Patrimonio bemA;   // esperado em A
    private Patrimonio bemB;   // esperado em B, será encontrado em A

    @BeforeEach
    void setUp() {
        UsuarioAutenticado admin = new UsuarioAutenticado(usuario("admin.teste", Perfil.ADMINISTRADOR));
        fiscal = new UsuarioAutenticado(usuario("fiscal.teste", Perfil.FISCAL));
        mvc = MockMvcBuilders.webAppContextSetup(ctx)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user(admin)))
                .build();

        // Garante que não há levantamento aberto de execuções anteriores (banco H2 compartilhado no contexto).
        levantamentoRepo.findFirstByStatus(StatusLevantamento.ABERTO)
                .ifPresent(l -> { l.concluir("setup"); levantamentoRepo.save(l); });

        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        salaA = lotacaoRepo.save(Lotacao.builder().upm("UPM TESTE " + sufixo).nome("SALA A").tipoLocal(TipoLocal.INTERNO).build());
        salaB = lotacaoRepo.save(Lotacao.builder().upm("UPM TESTE " + sufixo).nome("SALA B").tipoLocal(TipoLocal.INTERNO).build());
        Responsavel resp = responsavelRepo.save(Responsavel.builder()
                .nomeCompleto("Responsável Levantamento " + sufixo).matricula("LEV-" + sufixo).ativo(true).build());

        bemA = patrimonioRepo.save(Patrimonio.criar(request("TA-" + sufixo, "Notebook de teste", salaA.getId(), resp.getId()), salaA, resp));
        bemB = patrimonioRepo.save(Patrimonio.criar(request("TB-" + sufixo, "Cadeira de teste", salaB.getId(), resp.getId()), salaB, resp));
    }

    private Usuario usuario(String login, Perfil perfil) {
        return usuarioRepo.findByLogin(login).orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Usuário " + login)
                .login(login)
                .senhaHash(encoder.encode("segredo-teste"))
                .perfil(perfil)
                .ativo(true)
                .build()));
    }

    private static PatrimonioRequest request(String tombo, String descricao, Long lotacaoId, Long responsavelId) {
        return new PatrimonioRequest(tombo, descricao, "EQUIPAMENTO", null, LocalDate.now().minusYears(1), new BigDecimal("1000.00"),
                Conservacao.BOM, null, null, null, null, null, lotacaoId, responsavelId);
    }

    @Test
    void fluxoCompletoDoLevantamento() throws Exception {
        // 1) Abrir — todos os bens não baixados viram itens.
        mvc.perform(post("/api/levantamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ano\":2026,\"descricao\":\"Levantamento de teste\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTO"));

        LevantamentoPatrimonial lev = levantamentoRepo.findFirstByStatus(StatusLevantamento.ABERTO).orElseThrow();
        ItemLevantamento itemA = itemRepo.findByLevantamentoIdAndPatrimonioId(lev.getId(), bemA.getId()).orElseThrow();
        ItemLevantamento itemB = itemRepo.findByLevantamentoIdAndPatrimonioId(lev.getId(), bemB.getId()).orElseThrow();
        assertThat(itemA.getLotacao().getId()).isEqualTo(salaA.getId());
        assertThat(itemA.getConservacaoAnterior()).isEqualTo(Conservacao.BOM);
        String base = "/api/levantamentos/" + lev.getId();

        // 2) Resumo da sessão (alerta da SPA), lista, guia por local e checklist da sala A.
        mvc.perform(get("/api/me/resumo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.levantamentoAberto.id").value(lev.getId()));
        mvc.perform(get("/api/levantamentos")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Levantamento de teste")));
        mvc.perform(get(base + "/guia")).andExpect(status().isOk())
                .andExpect(content().string(containsString("SALA A")))
                .andExpect(content().string(containsString("SALA B")));
        mvc.perform(get(base + "/locais/" + salaA.getId() + "/itens")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descricao").value("Notebook de teste"));

        // 3) Conferir bem A com avaria, nova conservação e foto.
        MockMultipartFile foto = new MockMultipartFile("foto", "bem.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3});
        mvc.perform(multipart(base + "/itens/" + itemA.getId() + "/conferir").file(foto)
                        .param("resultado", "AVARIADO")
                        .param("conservacaoVerificada", "RUIM")
                        .param("descricaoAvaria", "Tela trincada")
                        .param("lotacaoContextoId", salaA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("AVARIADO"));

        itemA = itemRepo.findById(itemA.getId()).orElseThrow();
        assertThat(itemA.getDescricaoAvaria()).isEqualTo("Tela trincada");
        assertThat(itemA.getVerificadoPor()).isEqualTo("admin.teste");
        assertThat(itemA.getFoto()).isNotNull();
        assertThat(patrimonioRepo.findById(bemA.getId()).orElseThrow().getConservacao()).isEqualTo(Conservacao.RUIM);
        assertThat(anexoRepo.findByPatrimonioId(bemA.getId()))
                .anyMatch(a -> a.getTipo() == TipoAnexo.FOTO && "bem.png".equals(a.getNomeOriginal()));

        // 4) Avaria sem descrição é rejeitada (item segue como estava).
        mvc.perform(multipart(base + "/itens/" + itemB.getId() + "/conferir")
                        .param("resultado", "AVARIADO")
                        .param("lotacaoContextoId", salaB.getId().toString()))
                .andExpect(status().is4xxClientError());
        assertThat(itemRepo.findById(itemB.getId()).orElseThrow().getResultado()).isEqualTo(ResultadoConferencia.PENDENTE);

        // 5) Bem B foi achado na sala A: localizar pelo tombo e conferir OK a partir da sala A.
        mvc.perform(post(base + "/locais/" + salaA.getId() + "/localizar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tombo\":\"" + bemB.getNumeroTombo() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotacaoEncontradaId").value(salaA.getId()));
        mvc.perform(multipart(base + "/itens/" + itemB.getId() + "/conferir")
                        .param("resultado", "OK")
                        .param("lotacaoContextoId", salaA.getId().toString()))
                .andExpect(status().isOk());
        itemB = itemRepo.findById(itemB.getId()).orElseThrow();
        assertThat(itemB.getResultado()).isEqualTo(ResultadoConferencia.OK);
        assertThat(itemB.isLocalDivergente()).isTrue();
        assertThat(itemRepo.contarDivergenciasDeLocal(lev.getId())).isEqualTo(1);

        // 5b) Ocorrências (avaria + divergência de local) e guia com contadores.
        mvc.perform(get(base + "/ocorrencias")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Tela trincada")))
                .andExpect(content().string(containsString("Cadeira de teste")));
        mvc.perform(get(base)).andExpect(status().isOk())
                .andExpect(jsonPath("$.avariados").value(1))
                .andExpect(jsonPath("$.divergenciasLocal").value(1));

        // 6) Regularizar: mover o bem para onde foi encontrado.
        mvc.perform(post(base + "/itens/" + itemB.getId() + "/movimentar")).andExpect(status().is2xxSuccessful());
        assertThat(patrimonioRepo.findById(bemB.getId()).orElseThrow().getLotacao().getId()).isEqualTo(salaA.getId());

        // 7) Exportações respondem antes e depois de concluir.
        mvc.perform(get(base + "/download").param("formato", "xlsx")).andExpect(status().isOk());
        mvc.perform(get(base + "/download").param("formato", "pdf")).andExpect(status().isOk());

        // 8) Concluir — e depois nenhuma conferência é aceita.
        mvc.perform(post(base + "/concluir")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));
        lev = levantamentoRepo.findById(lev.getId()).orElseThrow();
        assertThat(lev.getConcluidoPor()).isEqualTo("admin.teste");
        mvc.perform(post(base + "/itens/" + itemA.getId() + "/desfazer")).andExpect(status().is4xxClientError());
        assertThat(itemRepo.findById(itemA.getId()).orElseThrow().getResultado()).isEqualTo(ResultadoConferencia.AVARIADO);
        mvc.perform(get(base + "/download").param("formato", "xlsx")).andExpect(status().isOk());

        // 9) Não é possível abrir dois levantamentos ao mesmo tempo (o concluído não impede um novo).
        mvc.perform(post("/api/levantamentos").contentType(MediaType.APPLICATION_JSON).content("{\"ano\":2027}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/levantamentos").contentType(MediaType.APPLICATION_JSON).content("{\"ano\":2028}"))
                .andExpect(status().isConflict());
    }

    @Test
    void fiscalNaoAbreLevantamento() throws Exception {
        mvc.perform(post("/api/levantamentos").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON).content("{\"ano\":2026}"))
                .andExpect(status().isForbidden());
        // Mas acessa a listagem normalmente.
        mvc.perform(get("/api/levantamentos").with(user(fiscal))).andExpect(status().isOk());
    }
}

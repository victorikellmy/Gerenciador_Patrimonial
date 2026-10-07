package com.fundacao.gerenciador_patrimonial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundacao.gerenciador_patrimonial.domain.entity.ItemLevantamento;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.domain.enums.ResultadoConferencia;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusLevantamento;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.repository.ItemLevantamentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.LevantamentoPatrimonialRepository;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API REST consumida pelo app Android (HTTP Basic, {@code /api/**}):
 * sessão, levantamento patrimonial ponta a ponta, catálogos, pendências e dashboard.
 */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha="
})
@AutoConfigureMockMvc
class ApiMobileFluxoTest {

    private static final String SENHA = "segredo-api";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired LevantamentoPatrimonialRepository levantamentoRepo;
    @Autowired ItemLevantamentoRepository itemRepo;
    @Autowired PatrimonioRepository patrimonioRepo;
    @Autowired LotacaoRepository lotacaoRepo;
    @Autowired ResponsavelRepository responsavelRepo;

    private Lotacao salaA;
    private Lotacao salaB;
    private Patrimonio bemA;
    private Patrimonio bemB;

    @BeforeEach
    void setUp() {
        usuario("api.admin", Perfil.ADMINISTRADOR);
        usuario("api.fiscal", Perfil.FISCAL);
        levantamentoRepo.findFirstByStatus(StatusLevantamento.ABERTO)
                .ifPresent(l -> { l.concluir("setup"); levantamentoRepo.save(l); });

        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        salaA = lotacaoRepo.save(Lotacao.builder().upm("UPM API " + sufixo).nome("SALA A").tipoLocal(TipoLocal.INTERNO).build());
        salaB = lotacaoRepo.save(Lotacao.builder().upm("UPM API " + sufixo).nome("SALA B").tipoLocal(TipoLocal.INTERNO).build());
        Responsavel resp = responsavelRepo.save(Responsavel.builder()
                .nomeCompleto("Responsável API " + sufixo).matricula("API-" + sufixo).ativo(true).build());
        bemA = patrimonioRepo.save(Patrimonio.criar(request("XA-" + sufixo, "Impressora API", salaA.getId(), resp.getId()), salaA, resp));
        bemB = patrimonioRepo.save(Patrimonio.criar(request("XB-" + sufixo, "Mesa API", salaB.getId(), resp.getId()), salaB, resp));
    }

    private RequestPostProcessor admin()  { return httpBasic("api.admin", SENHA); }
    private RequestPostProcessor fiscal() { return httpBasic("api.fiscal", SENHA); }

    @Test
    void semCredenciaisRecebe401ENao302() throws Exception {
        mvc.perform(get("/api/lotacoes").param("size", "1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/lotacoes").param("size", "1").with(httpBasic("x", "y")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessaoCatalogosDashboardEPendencias() throws Exception {
        mvc.perform(get("/api/me").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("api.fiscal"))
                .andExpect(jsonPath("$.perfil").value("FISCAL"));

        mvc.perform(get("/api/catalogos/upms").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/catalogos/subcategorias").with(fiscal())).andExpect(status().isOk());
        mvc.perform(get("/api/lotacoes/todas").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome").isArray());
        mvc.perform(get("/api/responsaveis/todos").with(fiscal())).andExpect(status().isOk());

        mvc.perform(get("/api/dashboard").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPatrimonios").isNumber());

        mvc.perform(get("/api/pendencias/contagem").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendentes").isNumber());
        mvc.perform(get("/api/pendencias").param("status", "PENDENTE").param("size", "5").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mvc.perform(get("/api/patrimonios/" + bemA.getId() + "/movimentacoes").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Usuários: só ADMIN.
        mvc.perform(get("/api/usuarios").with(fiscal())).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").with(admin())).andExpect(status().isOk());
    }

    @Test
    void fluxoDoLevantamentoPelaApi() throws Exception {
        // Nenhum aberto → 204.
        mvc.perform(get("/api/levantamentos/em-andamento").with(admin()))
                .andExpect(status().isNoContent());

        // FISCAL não abre; ADMIN abre (201).
        mvc.perform(post("/api/levantamentos").with(fiscal())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ano\":2026}"))
                .andExpect(status().isForbidden());
        MvcResult aberto = mvc.perform(post("/api/levantamentos").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ano\":2026,\"descricao\":\"Levantamento API\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andReturn();
        long levId = json.readTree(aberto.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/levantamentos/em-andamento").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(levId));

        // Guia: lista por UPM com os locais.
        MvcResult guia = mvc.perform(get("/api/levantamentos/" + levId + "/guia").with(fiscal()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode grupos = json.readTree(guia.getResponse().getContentAsString());
        assertThat(grupos.isArray()).isTrue();
        JsonNode grupo = null;
        for (JsonNode g : grupos) if (g.get("upm").asText().equals(salaA.getUpm())) grupo = g;
        assertThat(grupo).isNotNull();
        assertThat(grupo.get("locais")).hasSize(2);
        assertThat(grupo.get("locais").get(0).get("total").asLong()).isEqualTo(1);

        // Itens do local A.
        mvc.perform(get("/api/levantamentos/" + levId + "/locais/" + salaA.getId() + "/itens").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].descricao").value("Impressora API"))
                .andExpect(jsonPath("$[0].resultado").value("PENDENTE"));

        ItemLevantamento itemA = itemRepo.findByLevantamentoIdAndPatrimonioId(levId, bemA.getId()).orElseThrow();
        ItemLevantamento itemB = itemRepo.findByLevantamentoIdAndPatrimonioId(levId, bemB.getId()).orElseThrow();

        // Conferir com avaria + foto (multipart) pelo FISCAL.
        MockMultipartFile foto = new MockMultipartFile("foto", "bem.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3});
        mvc.perform(multipart("/api/levantamentos/" + levId + "/itens/" + itemA.getId() + "/conferir")
                        .file(foto).with(fiscal())
                        .param("resultado", "AVARIADO")
                        .param("conservacaoVerificada", "RUIM")
                        .param("descricaoAvaria", "Bandeja quebrada")
                        .param("lotacaoContextoId", salaA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("AVARIADO"))
                .andExpect(jsonPath("$.fotoId").isNumber())
                .andExpect(jsonPath("$.verificadoPor").value("api.fiscal"));
        assertThat(patrimonioRepo.findById(bemA.getId()).orElseThrow().getConservacao()).isEqualTo(Conservacao.RUIM);

        // Regra de negócio vira 409 JSON.
        mvc.perform(multipart("/api/levantamentos/" + levId + "/itens/" + itemB.getId() + "/conferir")
                        .with(fiscal())
                        .param("resultado", "AVARIADO")
                        .param("lotacaoContextoId", salaB.getId().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").exists());

        // Localizar bem B na sala A e conferir OK → divergência; ocorrências listam os dois.
        mvc.perform(post("/api/levantamentos/" + levId + "/locais/" + salaA.getId() + "/localizar").with(fiscal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tombo\":\"" + bemB.getNumeroTombo() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotacaoEncontradaId").value(salaA.getId()));
        mvc.perform(multipart("/api/levantamentos/" + levId + "/itens/" + itemB.getId() + "/conferir")
                        .with(fiscal())
                        .param("resultado", "OK")
                        .param("lotacaoContextoId", salaA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("OK"));
        mvc.perform(get("/api/levantamentos/" + levId + "/ocorrencias").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Movimentar para onde foi encontrado (204) e desfazer a conferência de A.
        mvc.perform(post("/api/levantamentos/" + levId + "/itens/" + itemB.getId() + "/movimentar").with(fiscal()))
                .andExpect(status().isNoContent());
        assertThat(patrimonioRepo.findById(bemB.getId()).orElseThrow().getLotacao().getId()).isEqualTo(salaA.getId());

        mvc.perform(post("/api/levantamentos/" + levId + "/itens/" + itemA.getId() + "/desfazer").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("PENDENTE"));
        assertThat(itemRepo.findById(itemA.getId()).orElseThrow().getResultado()).isEqualTo(ResultadoConferencia.PENDENTE);

        // Exportação e conclusão (FISCAL não conclui).
        mvc.perform(get("/api/levantamentos/" + levId + "/download").param("formato", "xlsx").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("levantamento_2026_")));
        mvc.perform(post("/api/levantamentos/" + levId + "/concluir").with(fiscal()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/levantamentos/" + levId + "/concluir").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));
        mvc.perform(get("/api/levantamentos/em-andamento").with(admin()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/levantamentos").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + levId + ")].status").value("CONCLUIDO"));
    }

    // ------------------------------------------------------------------ helpers

    private void usuario(String login, Perfil perfil) {
        if (usuarioRepo.existsByLogin(login)) return;
        usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Usuário " + login)
                .login(login)
                .senhaHash(encoder.encode(SENHA))
                .perfil(perfil)
                .ativo(true)
                .build());
    }

    private static PatrimonioRequest request(String tombo, String descricao, Long lotacaoId, Long responsavelId) {
        return new PatrimonioRequest(tombo, descricao, "EQUIPAMENTO", null, LocalDate.now().minusYears(1),
                new BigDecimal("500.00"), Conservacao.BOM, null, null, null, null, null, lotacaoId, responsavelId);
    }
}

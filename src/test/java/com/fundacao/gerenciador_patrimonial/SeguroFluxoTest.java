package com.fundacao.gerenciador_patrimonial;

import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.entity.Veiculo;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import com.fundacao.gerenciador_patrimonial.repository.VeiculoRepository;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import com.jayway.jsonpath.JsonPath;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seguro da frota pela API: apólice com arquivo → situação e alertas de vencimento →
 * renovação tira o alerta → sinistro ligado à apólice da data → permissões por perfil.
 */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha=",
        "app.frota.transportes.url=",
        "app.frota.seed=false"
})
@AutoConfigureMockMvc
class SeguroFluxoTest {

    private MockMvc mvc;

    @Autowired WebApplicationContext ctx;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired LotacaoRepository lotacaoRepo;
    @Autowired ResponsavelRepository responsavelRepo;
    @Autowired VeiculoRepository veiculoRepo;

    private Lotacao lotacao;
    private Responsavel responsavel;

    @BeforeEach
    void setUp() {
        UsuarioAutenticado admin = new UsuarioAutenticado(usuario("admin.seguro", Perfil.ADMINISTRADOR));
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity())
                .defaultRequest(get("/").with(user(admin))).build();
        String sufixo = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        lotacao = lotacaoRepo.save(Lotacao.builder().upm("FROTA SEG " + sufixo).nome("GARAGEM").tipoLocal(TipoLocal.INTERNO).build());
        responsavel = responsavelRepo.save(Responsavel.builder().nomeCompleto("Resp Seguro " + sufixo).matricula("SG-" + sufixo).ativo(true).build());
    }

    private Usuario usuario(String login, Perfil perfil) {
        return usuarioRepo.findByLogin(login).orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Usuário " + login).login(login).senhaHash(encoder.encode("segredo-teste"))
                .perfil(perfil).ativo(true).build()));
    }

    private Veiculo novoVeiculo() throws Exception {
        String placa = "SEG-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        mvc.perform(post("/api/frota/veiculos").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placa":"%s","modelo":"STRADA","lotacaoId":%d,"responsavelId":%d}"""
                                .formatted(placa, lotacao.getId(), responsavel.getId())))
                .andExpect(status().isCreated());
        return veiculoRepo.findByPlacaIgnoreCase(placa).orElseThrow();
    }

    private static String seguroJson(long veiculoId, String apolice, LocalDate inicio, LocalDate fim) {
        return """
                {"veiculoId":%d,"seguradora":"porto seguro","numeroApolice":"%s","inicioVigencia":"%s","fimVigencia":"%s",
                 "valorPremio":2450.90,"franquia":3200.00,"coberturas":"Casco, terceiros e vidros","telefoneAssistencia":"0800 727 0800"}"""
                .formatted(veiculoId, apolice, inicio, fim);
    }

    private long id(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return ((Number) JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void apoliceAlertasRenovacaoESinistro() throws Exception {
        LocalDate hoje = LocalDate.now();
        Veiculo v = novoVeiculo();

        // Sem apólice: aparece em "sem seguro"
        mvc.perform(get("/api/frota/seguros/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semSeguro[*].placa", hasItem(v.getPlaca())));

        // Apólice que vence em 10 dias, com o PDF da apólice → situação VENCENDO e alerta
        String dados = seguroJson(v.getId(), "AP-1001", hoje.minusDays(355), hoje.plusDays(10));
        var criado = mvc.perform(multipart("/api/frota/seguros")
                        .file(new MockMultipartFile("dados", "", "application/json", dados.getBytes()))
                        .file(new MockMultipartFile("arquivo", "apolice.pdf", "application/pdf", "%PDF-1.4".getBytes())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seguradora").value("PORTO SEGURO"))
                .andExpect(jsonPath("$.situacao").value("VENCENDO"))
                .andExpect(jsonPath("$.diasParaVencer").value(10))
                .andExpect(jsonPath("$.possuiApolice").value(true));
        long seguroId = id(criado);
        mvc.perform(get("/api/frota/seguros/" + seguroId + "/apolice"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/frota/seguros/alertas"))
                .andExpect(jsonPath("$.vencendo[*].placa", hasItem(v.getPlaca())))
                .andExpect(jsonPath("$.semSeguro[*].placa", not(hasItem(v.getPlaca()))));

        // Renovação cadastrada (começa depois do fim da atual) → sai do alerta "vencendo"
        mvc.perform(post("/api/frota/seguros").contentType(MediaType.APPLICATION_JSON)
                        .content(seguroJson(v.getId(), "AP-2002", hoje.plusDays(11), hoje.plusDays(376))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("FUTURO"));
        mvc.perform(get("/api/frota/seguros/alertas"))
                .andExpect(jsonPath("$.vencendo[*].placa", not(hasItem(v.getPlaca()))));
        mvc.perform(get("/api/frota/seguros").param("veiculoId", v.getId().toString()))
                .andExpect(jsonPath("$.length()").value(2));

        // Fim antes do início → recusado
        mvc.perform(post("/api/frota/seguros").contentType(MediaType.APPLICATION_JSON)
                        .content(seguroJson(v.getId(), "AP-X", hoje, hoje.minusDays(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("fim da vigência")));

        // Sinistro sem apólice informada → liga à apólice que cobria a data
        var sinistro = mvc.perform(post("/api/frota/sinistros").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"veiculoId":%d,"dataOcorrencia":"%s","descricao":"Colisão traseira no estacionamento","valorPrejuizo":4800}"""
                                .formatted(v.getId(), hoje.minusDays(2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seguroId").value(seguroId))
                .andExpect(jsonPath("$.numeroApolice").value("AP-1001"))
                .andExpect(jsonPath("$.situacao").value("ABERTO"))
                .andExpect(jsonPath("$.emAndamento").value(true));
        long sinistroId = id(sinistro);
        mvc.perform(get("/api/frota/seguros/alertas"))
                .andExpect(jsonPath("$.sinistrosEmAndamento[*].id", hasItem((int) sinistroId)));

        // Apólice com sinistro não pode ser excluída
        mvc.perform(delete("/api/frota/seguros/" + seguroId)).andExpect(status().isConflict());

        // Indenizado → sai dos sinistros em andamento
        mvc.perform(put("/api/frota/sinistros/" + sinistroId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"veiculoId":%d,"seguroId":%d,"dataOcorrencia":"%s","descricao":"Colisão traseira no estacionamento","valorPrejuizo":4800,"valorIndenizado":1600,"situacao":"INDENIZADO"}"""
                                .formatted(v.getId(), seguroId, hoje.minusDays(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emAndamento").value(false));
        mvc.perform(get("/api/frota/seguros/alertas"))
                .andExpect(jsonPath("$.sinistrosEmAndamento[*].id", not(hasItem((int) sinistroId))));
    }

    @Test
    void apoliceVencidaSemRenovacaoEPermissoes() throws Exception {
        LocalDate hoje = LocalDate.now();
        Veiculo v = novoVeiculo();
        mvc.perform(post("/api/frota/seguros").contentType(MediaType.APPLICATION_JSON)
                        .content(seguroJson(v.getId(), "AP-OLD", hoje.minusYears(1), hoje.minusDays(3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("VENCIDO"));
        mvc.perform(get("/api/frota/seguros/alertas"))
                .andExpect(jsonPath("$.vencidos[*].placa", hasItem(v.getPlaca())));
        mvc.perform(get("/api/frota/seguros").param("situacao", "VENCIDO"))
                .andExpect(jsonPath("$[*].numeroApolice", hasItem("AP-OLD")));

        // Motorista e gerente consultam (apólice, assistência 24h), mas não cadastram
        UsuarioAutenticado motorista = new UsuarioAutenticado(usuario("motorista.seguro", Perfil.MOTORISTA));
        UsuarioAutenticado gerente = new UsuarioAutenticado(usuario("gerente.seguro", Perfil.GERENTE));
        for (UsuarioAutenticado u : new UsuarioAutenticado[]{motorista, gerente}) {
            mvc.perform(get("/api/frota/seguros").param("veiculoId", v.getId().toString()).with(user(u))).andExpect(status().isOk());
            mvc.perform(get("/api/frota/seguros/alertas").with(user(u))).andExpect(status().isOk());
            mvc.perform(post("/api/frota/seguros").with(user(u)).contentType(MediaType.APPLICATION_JSON)
                            .content(seguroJson(v.getId(), "AP-NEGADA", hoje, hoje.plusYears(1))))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/frota/sinistros").with(user(u)).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"veiculoId":%d,"dataOcorrencia":"%s","descricao":"x"}""".formatted(v.getId(), hoje)))
                    .andExpect(status().isForbidden());
        }

        // Presidente gere a frota: cadastra
        UsuarioAutenticado presidente = new UsuarioAutenticado(usuario("presidente.seguro", Perfil.PRESIDENTE));
        mvc.perform(post("/api/frota/seguros").with(user(presidente)).contentType(MediaType.APPLICATION_JSON)
                        .content(seguroJson(v.getId(), "AP-PRES", hoje, hoje.plusYears(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.situacao").value("VIGENTE"));
        mvc.perform(get("/api/frota/seguros/alertas"))
                .andExpect(jsonPath("$.vencidos[*].placa", not(hasItem(v.getPlaca()))));
    }
}

package com.fundacao.gerenciador_patrimonial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundacao.gerenciador_patrimonial.domain.entity.AuditoriaAcao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.repository.AbastecimentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.AuditoriaAcaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.NivelDiariaRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão dos seis itens da auditoria de segurança run-1 (19/10/2026).
 *
 * <p>Os seis foram confirmados localmente (H2, dados fictícios, sem rede) com a versão
 * anterior deste teste, que afirmava o comportamento vulnerável. Os itens 1 a 4 e 6 foram
 * corrigidos e aqui afirmam o comportamento seguro. O item 5 depende da implantação (HTTPS)
 * e o teste dele fica desativado. Os testes de endurecimento cobrem os achados do crítico.</p>
 */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha=",
        "app.frota.transportes.url=",
        "app.frota.seed=false"
})
@AutoConfigureMockMvc
class AuditoriaSegurancaValidacaoTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired LotacaoRepository lotacaoRepo;
    @Autowired ResponsavelRepository responsavelRepo;
    @Autowired AbastecimentoRepository abastecimentoRepo;
    @Autowired NivelDiariaRepository nivelRepo;
    @Autowired AuditoriaAcaoRepository auditoriaRepo;
    @Autowired ServerProperties serverProperties;
    @Autowired ObjectMapper json;
    @Value("${app.storage.pasta-raiz}") String pastaRaiz;

    private MockMvc mvc;
    private UsuarioAutenticado admin;
    private UsuarioAutenticado fiscal;
    private UsuarioAutenticado motorista;
    private String sufixo;

    @BeforeEach
    void setUp() {
        admin = new UsuarioAutenticado(usuario("val.admin", Perfil.ADMINISTRADOR));
        fiscal = new UsuarioAutenticado(usuario("val.fiscal", Perfil.FISCAL));
        motorista = new UsuarioAutenticado(usuario("val.motorista", Perfil.MOTORISTA));
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        sufixo = UUID.randomUUID().toString().substring(0, 5).toUpperCase();
    }

    private Usuario usuario(String login, Perfil perfil) {
        return usuarioRepo.findByLogin(login).orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Usuário " + login).login(login).senhaHash(encoder.encode("segredo-teste"))
                .perfil(perfil).ativo(true).build()));
    }

    private Usuario novoUsuario(String prefixo, Perfil perfil, String senha, boolean provisoria) {
        return usuarioRepo.save(Usuario.builder().nomeCompleto("Usuário " + prefixo)
                .login(prefixo + "." + sufixo.toLowerCase()).senhaHash(encoder.encode(senha))
                .perfil(perfil).ativo(true).senhaProvisoria(provisoria).build());
    }

    private MockHttpSession login(String login, String senha) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"senha\":\"%s\"}".formatted(login, senha)))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    // ------------------------------------------------------------------ item 1 (corrigido)

    /** Não existe mais senha padrão no código: o admin do boot não aceita o antigo valor fixo. */
    @Test
    void item1_adminInicialSemSenhaPadrao() throws Exception {
        Usuario bootstrap = usuarioRepo.findByLogin("admin").orElseThrow();
        assertThat(bootstrap.isSenhaProvisoria()).isTrue();
        assertThat(encoder.matches("trocar@123", bootstrap.getSenhaHash())).isFalse();
        mvc.perform(get("/api/usuarios").with(httpBasic("admin", "trocar@123"))).andExpect(status().isUnauthorized());
    }

    /** Senha provisória é barrada no servidor (Basic e sessão): só /api/me/** e /api/auth/** até a troca. */
    @Test
    void item1_senhaProvisoriaBloqueadaNoServidor() throws Exception {
        Usuario prov = novoUsuario("val.prov", Perfil.ADMINISTRADOR, "provisoria-1", true);
        mvc.perform(get("/api/usuarios").with(httpBasic(prov.getLogin(), "provisoria-1"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/me").with(httpBasic(prov.getLogin(), "provisoria-1"))).andExpect(status().isOk());

        MockHttpSession sessao = login(prov.getLogin(), "provisoria-1");
        mvc.perform(get("/api/usuarios").session(sessao)).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/senha").session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"provisoria-1\",\"novaSenha\":\"definitiva-2\",\"confirmacao\":\"definitiva-2\"}"))
                .andExpect(status().isNoContent());
        // a própria sessão segue válida e liberada depois da troca
        mvc.perform(get("/api/usuarios").session(sessao)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ item 2 (corrigido)

    /** Motorista não troca a NF de um lançamento existente; o fiscal troca e a NF anterior é mantida. */
    @Test
    void item2_nfDeLancamentoExistenteSoAdminOuFiscal() throws Exception {
        long veiculoId = criarVeiculo("NF");
        String dados = """
                {"veiculoId":%d,"data":"%s","km":1000,"combustivel":"GASOLINA","quantidadeLitros":30,"valor":150.00,"motorista":"Fiscal"}"""
                .formatted(veiculoId, LocalDate.now());
        String resp = mvc.perform(multipart("/api/frota/veiculos/" + veiculoId + "/abastecimentos")
                        .file(new MockMultipartFile("dados", "", "application/json", dados.getBytes()))
                        .file(new MockMultipartFile("arquivo", "original.pdf", "application/pdf", "%PDF-A".getBytes()))
                        .with(user(fiscal)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long abastId = json.readTree(resp).get("id").asLong();
        String caminhoOriginal = abastecimentoRepo.findById(abastId).orElseThrow().getNfCaminho();

        mvc.perform(multipart("/api/frota/abastecimentos/" + abastId + "/nota-fiscal")
                        .file(new MockMultipartFile("arquivo", "trocada.png", "image/png", "PNG-B".getBytes()))
                        .with(user(motorista)))
                .andExpect(status().isForbidden());
        assertThat(abastecimentoRepo.findById(abastId).orElseThrow().getNfNomeOriginal()).isEqualTo("original.pdf");

        mvc.perform(multipart("/api/frota/abastecimentos/" + abastId + "/nota-fiscal")
                        .file(new MockMultipartFile("arquivo", "corrigida.pdf", "application/pdf", "%PDF-C".getBytes()))
                        .with(user(fiscal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nfNomeOriginal").value("corrigida.pdf"));
        assertThat(Files.exists(Path.of(pastaRaiz).resolve(caminhoOriginal))).isTrue();

        // o motorista continua anexando a NF no próprio cadastro (fluxo normal)
        mvc.perform(multipart("/api/frota/veiculos/" + veiculoId + "/abastecimentos")
                        .file(new MockMultipartFile("dados", "", "application/json", dados.getBytes()))
                        .file(new MockMultipartFile("arquivo", "cupom.jpg", "image/jpeg", "JPG".getBytes()))
                        .with(user(motorista)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ item 3 (corrigido)

    /** Desativar um usuário derruba a sessão que ele já tinha. */
    @Test
    void item3_desativacaoEncerraSessao() throws Exception {
        Usuario alvo = novoUsuario("val.alvo", Perfil.ADMINISTRADOR, "segredo-alvo", false);
        MockHttpSession sessao = login(alvo.getLogin(), "segredo-alvo");
        mvc.perform(get("/api/usuarios").session(sessao)).andExpect(status().isOk());

        mvc.perform(delete("/api/usuarios/" + alvo.getId()).with(user(admin))).andExpect(status().is2xxSuccessful());
        mvc.perform(get("/api/usuarios").session(sessao)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").session(sessao)).andExpect(status().isUnauthorized());
    }

    /** Rebaixar o perfil derruba a sessão com o perfil antigo. */
    @Test
    void item3_mudancaDePerfilEncerraSessao() throws Exception {
        Usuario alvo = novoUsuario("val.rebaixado", Perfil.ADMINISTRADOR, "segredo-reb", false);
        MockHttpSession sessao = login(alvo.getLogin(), "segredo-reb");
        mvc.perform(put("/api/usuarios/" + alvo.getId()).with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto":"Rebaixado","login":"%s","perfil":"GERENTE","ativo":true,"exigirTrocaSenha":false}"""
                                .formatted(alvo.getLogin())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/usuarios").session(sessao)).andExpect(status().isUnauthorized());
        // um novo login entra já com o perfil novo
        MockHttpSession nova = login(alvo.getLogin(), "segredo-reb");
        mvc.perform(get("/api/usuarios").session(nova)).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard").session(nova)).andExpect(status().isOk());
    }

    /** Trocar a própria senha mantém a sessão atual e derruba as outras sessões do usuário. */
    @Test
    void item3_trocaDeSenhaEncerraOutrasSessoes() throws Exception {
        Usuario alvo = novoUsuario("val.troca", Perfil.FISCAL, "senha-antiga", false);
        MockHttpSession a = login(alvo.getLogin(), "senha-antiga");
        MockHttpSession b = login(alvo.getLogin(), "senha-antiga");
        mvc.perform(post("/api/me/senha").session(a).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"senha-antiga\",\"novaSenha\":\"senha-nova-1\",\"confirmacao\":\"senha-nova-1\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(a)).andExpect(status().isOk());
        mvc.perform(get("/api/me").session(b)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ item 4 (corrigido)

    /** Editar uma diária já deferida devolve para SOLICITADO: não dá para pagar sem nova aprovação. */
    @Test
    void item4_diariaDeferidaEditadaVoltaParaAprovacao() throws Exception {
        Long nivelId = nivelRepo.findByAtivoTrueOrderByIdAsc().get(0).getId();
        long prof = criarProfissional("Prof " + sufixo, nivelId);
        UsuarioAutenticado presidente = new UsuarioAutenticado(usuario("val.presidente", Perfil.PRESIDENTE));
        UsuarioAutenticado financeiro = new UsuarioAutenticado(usuario("val.financeiro", Perfil.FINANCEIRO));
        String corpo = """
                {"profissionalId":%d,"itinerario":"Palmas - Gurupi","partida":"%s","retorno":"%s","meioTransporte":"VEICULO_OFICIAL",
                 "recurso":"PROPRIO","numeroDiarias":%s,"valorUnitario":%s}""";
        String criada = mvc.perform(post("/api/diarias").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo.formatted(prof, LocalDate.now().plusDays(2), LocalDate.now().plusDays(3), "1", "100")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(criada).get("id").asLong();
        mvc.perform(post("/api/diarias/" + id + "/confirmar").with(user(admin)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/diarias/" + id + "/deferir").with(user(presidente)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.status").value("DEFERIDO"));

        mvc.perform(put("/api/diarias/" + id).with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content(corpo.formatted(prof, LocalDate.now().plusDays(2), LocalDate.now().plusDays(3), "10", "5000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLICITADO"));
        mvc.perform(post("/api/diarias/" + id + "/pagar").with(user(financeiro)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------ item 5 (implantação)

    @Test
    @Disabled("Item 5 depende da implantação: HTTPS no proxy + SESSION_COOKIE_SECURE=true e APP_BIND=127.0.0.1 no servidor.")
    void item5_cookieDeSessaoSecure() {
        assertThat(serverProperties.getServlet().getSession().getCookie().getSecure()).isTrue();
    }

    // ------------------------------------------------------------------ item 6 (corrigido)

    /** O IP da auditoria é o da conexão; um X-Forwarded-For enviado pelo cliente não é gravado. */
    @Test
    void item6_ipDeAuditoriaNaoVemDoCliente() throws Exception {
        assertThat(ipGravado(mvc)).isEqualTo("127.0.0.1");
    }

    // ------------------------------------------------------------------ endurecimento

    /** Depois de 10 senhas erradas o login fica bloqueado (429), mesmo com a senha certa. */
    @Test
    void login_bloqueiaDepoisDeFalhasSeguidas() throws Exception {
        Usuario alvo = novoUsuario("val.forca", Perfil.FISCAL, "senha-certa", false);
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"login\":\"%s\",\"senha\":\"errada-%d\"}".formatted(alvo.getLogin(), i)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"senha\":\"senha-certa\"}".formatted(alvo.getLogin())))
                .andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/me").with(httpBasic(alvo.getLogin(), "senha-certa"))).andExpect(status().isUnauthorized());
    }

    /** Usuário desativado recebe a mesma resposta de senha errada (não revela que a conta existe). */
    @Test
    void login_inativoMesmaMensagemDeSenhaErrada() throws Exception {
        Usuario inativo = novoUsuario("val.inativo", Perfil.FISCAL, "senha-ok", false);
        inativo.setAtivo(false);
        usuarioRepo.save(inativo);
        String inativoResp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"senha\":\"senha-ok\"}".formatted(inativo.getLogin())))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String erradaResp = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"senha\":\"outra\"}".formatted(inativo.getLogin() + "x")))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(inativoResp).path("mensagem")).isEqualTo(json.readTree(erradaResp).path("mensagem"));
    }

    /** Veículo desativado não recebe lançamento novo. */
    @Test
    void frota_veiculoDesativadoNaoRecebeLancamento() throws Exception {
        long veiculoId = criarVeiculo("INA");
        mvc.perform(post("/api/frota/veiculos/" + veiculoId + "/ativo").param("ativo", "false").with(user(admin)))
                .andExpect(status().isOk());
        String dados = """
                {"veiculoId":%d,"data":"%s","km":1000,"combustivel":"GASOLINA","quantidadeLitros":30,"valor":150.00,"motorista":"Fiscal"}"""
                .formatted(veiculoId, LocalDate.now());
        mvc.perform(multipart("/api/frota/veiculos/" + veiculoId + "/abastecimentos")
                        .file(new MockMultipartFile("dados", "", "application/json", dados.getBytes()))
                        .with(user(fiscal)))
                .andExpect(status().isConflict());
    }

    /** NF com tipo não permitido é recusada antes de o lançamento ser gravado. */
    @Test
    void frota_nfComTipoInvalidoRecusada() throws Exception {
        long veiculoId = criarVeiculo("TIP");
        long antes = abastecimentoRepo.count();
        String dados = """
                {"veiculoId":%d,"data":"%s","km":1000,"combustivel":"GASOLINA","quantidadeLitros":30,"valor":150.00,"motorista":"Motorista"}"""
                .formatted(veiculoId, LocalDate.now());
        mvc.perform(multipart("/api/frota/veiculos/" + veiculoId + "/abastecimentos")
                        .file(new MockMultipartFile("dados", "", "application/json", dados.getBytes()))
                        .file(new MockMultipartFile("arquivo", "pagina.html", "text/html", "<script>x</script>".getBytes()))
                        .with(user(motorista)))
                .andExpect(status().is4xxClientError());
        assertThat(abastecimentoRepo.count()).isEqualTo(antes);
    }

    /** Cadastro de profissionais (CPF, dados bancários) é só de ADMIN/FISCAL. */
    @Test
    void diarias_profissionaisSoAdminOuFiscal() throws Exception {
        UsuarioAutenticado gerente = new UsuarioAutenticado(usuario("val.gerente", Perfil.GERENTE));
        mvc.perform(get("/api/diarias/profissionais").with(user(gerente))).andExpect(status().isForbidden());
        mvc.perform(get("/api/diarias/profissionais").with(user(motorista))).andExpect(status().isForbidden());
        mvc.perform(get("/api/diarias/profissionais").with(user(fiscal))).andExpect(status().isOk());
    }

    /** A integração recusa quantidade que viraria milhões de bens. */
    @Test
    void integracao_limitesDoRecebimento() throws Exception {
        UsuarioAutenticado integ = new UsuarioAutenticado(usuario("val.integ", Perfil.INTEGRACAO));
        mvc.perform(post("/api/integracao/almoxarifado/recebimentos").with(user(integ)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compraId\":%d,\"itens\":[{\"descricao\":\"CADEIRA\",\"quantidade\":2000000000}]}"
                                .formatted(900_000 + new java.util.Random().nextInt(99_999))))
                .andExpect(status().isBadRequest());
    }

    /** JSON com tipo errado devolve só o nome do campo, sem a mensagem interna do parser. */
    @Test
    void erros_naoExpoemDetalhesInternos() throws Exception {
        String r = mvc.perform(post("/api/frota/veiculos").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"a\",\"modelo\":\"GOL\",\"lotacaoId\":\"nao-numero\"}"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(r).contains("lotacaoId").doesNotContain("java.").doesNotContain("Cannot deserialize");
    }

    /** Presidente consulta e edita toda a frota como o fiscal; excluir e desativar seguem só com o ADMIN. */
    @Test
    void frota_presidenteConsultaEEditaAFrota() throws Exception {
        UsuarioAutenticado presidente = new UsuarioAutenticado(usuario("val.presidente", Perfil.PRESIDENTE));
        long veiculoId = criarVeiculo("PRE");
        mvc.perform(get("/api/frota/veiculos").with(user(presidente))).andExpect(status().isOk());
        mvc.perform(get("/api/frota/veiculos/" + veiculoId).with(user(presidente))).andExpect(status().isOk());
        mvc.perform(get("/api/frota/veiculos/" + veiculoId + "/historico").with(user(presidente))).andExpect(status().isOk());
        mvc.perform(get("/api/frota/abastecimentos").with(user(presidente))).andExpect(status().isOk());
        mvc.perform(get("/api/frota/cautelas").with(user(presidente))).andExpect(status().isOk());
        mvc.perform(get("/api/frota/gestao").with(user(presidente))).andExpect(status().isOk());

        String dados = """
                {"veiculoId":%d,"data":"%s","km":1000,"combustivel":"GASOLINA","quantidadeLitros":30,"valor":150.00,"motorista":"X"}"""
                .formatted(veiculoId, LocalDate.now());
        mvc.perform(multipart("/api/frota/veiculos/" + veiculoId + "/abastecimentos")
                        .file(new MockMultipartFile("dados", "", "application/json", dados.getBytes()))
                        .with(user(presidente)))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/frota/veiculos/" + veiculoId).with(user(presidente)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"PRE-%s\",\"modelo\":\"GOL EDITADO\"}".formatted(sufixo.substring(0, 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("GOL EDITADO"));
        mvc.perform(post("/api/frota/veiculos/" + veiculoId + "/manutencao-status").param("emManutencao", "true").with(user(presidente)))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(delete("/api/frota/veiculos/" + veiculoId).with(user(presidente))).andExpect(status().isForbidden());
        mvc.perform(post("/api/frota/veiculos/" + veiculoId + "/ativo").param("ativo", "false").with(user(presidente)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ apoio

    /** Cadastra um veículo (ação auditada) enviando X-Forwarded-For forjado e devolve o IP gravado. */
    private String ipGravado(MockMvc cliente) throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        Lotacao lot = lotacaoRepo.save(Lotacao.builder().upm("XFF " + s).nome("GARAGEM").tipoLocal(TipoLocal.INTERNO).build());
        Responsavel resp = responsavelRepo.save(Responsavel.builder().nomeCompleto("Resp XFF " + s).matricula("X-" + s).ativo(true).build());
        cliente.perform(post("/api/frota/veiculos").with(user(fiscal)).with(r -> { r.setRemoteAddr("127.0.0.1"); return r; })
                        .header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"XFF-%s\",\"modelo\":\"GOL\",\"lotacaoId\":%d,\"responsavelId\":%d}".formatted(s, lot.getId(), resp.getId())))
                .andExpect(status().isCreated());
        AuditoriaAcao ultima = auditoriaRepo.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(a -> "val.fiscal".equals(a.getUsuario())).findFirst().orElseThrow();
        return ultima.getIpOrigem();
    }

    private long criarVeiculo(String prefixo) throws Exception {
        Lotacao lot = lotacaoRepo.save(Lotacao.builder().upm("VAL " + sufixo).nome("GARAGEM").tipoLocal(TipoLocal.INTERNO).build());
        Responsavel resp = responsavelRepo.save(Responsavel.builder().nomeCompleto("Resp " + sufixo).matricula("V-" + sufixo).ativo(true).build());
        String r = mvc.perform(post("/api/frota/veiculos").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"%s-%s\",\"modelo\":\"GOL\",\"lotacaoId\":%d,\"responsavelId\":%d}"
                                .formatted(prefixo, sufixo.substring(0, 4), lot.getId(), resp.getId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("id").asLong();
    }

    private long criarProfissional(String nome, Long nivelId) throws Exception {
        String r = mvc.perform(post("/api/diarias/profissionais").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"%s\",\"nivelId\":%d,\"cpf\":\"%s\"}".formatted(nome, nivelId, cpfValido())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(r).get("id").asLong();
    }

    /** CPF fictício com dígitos verificadores válidos. */
    private static String cpfValido() {
        java.util.Random rnd = new java.util.Random();
        int[] d = new int[11];
        for (int i = 0; i < 9; i++) d[i] = rnd.nextInt(10);
        for (int k = 9; k <= 10; k++) {
            int soma = 0;
            for (int i = 0; i < k; i++) soma += d[i] * (k + 1 - i);
            int resto = (soma * 10) % 11;
            d[k] = resto == 10 ? 0 : resto;
        }
        StringBuilder sb = new StringBuilder();
        for (int x : d) sb.append(x);
        return sb.toString();
    }
}

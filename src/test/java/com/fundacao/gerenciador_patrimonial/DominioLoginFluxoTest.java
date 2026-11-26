package com.fundacao.gerenciador_patrimonial;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.OrigemUsuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login com a conta da rede (AD FPTO.LOCAL) e gestão dos grupos do Patrimônio no AD,
 * contra um servidor LDAP em memória com a mesma estrutura do Samba AD.
 */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha=",
        "app.frota.transportes.url=",
        "app.frota.seed=false"
})
class DominioLoginFluxoTest {

    private static final String BASE = "DC=fpto,DC=local";
    private static final String USERS = "CN=Users," + BASE;
    private static final String OU = "OU=Patrimonio," + BASE;
    private static final String SERVICO = "CN=svc-patrimonio," + USERS;
    private static final InMemoryDirectoryServer AD = iniciarAd();

    @DynamicPropertySource
    static void ad(DynamicPropertyRegistry r) {
        r.add("app.ad.habilitado", () -> "true");
        r.add("app.ad.urls", () -> "ldap://127.0.0.1:" + AD.getListenPort());
        r.add("app.ad.dominio", () -> "fpto.local");
        r.add("app.ad.base", () -> BASE);
        r.add("app.ad.usuario-servico", () -> SERVICO);
        r.add("app.ad.senha-servico", () -> "Servico#123");
        r.add("app.ad.ou-grupos", () -> OU);
    }

    @AfterAll
    static void pararAd() {
        AD.shutDown(true);
    }

    private MockMvc mvc;
    @Autowired WebApplicationContext ctx;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        // Administrador local (não existe no AD): continua entrando pela senha do sistema.
        usuarioRepo.findByLogin("admin.ad").orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Admin Local").login("admin.ad").senhaHash(encoder.encode("Admin#Local1"))
                .perfil(Perfil.ADMINISTRADOR).ativo(true).build()));
    }

    @Test
    void contaDaRedeSemGrupoEntraComoUsuarioPadraoSemAcessoAosModulos() throws Exception {
        MockHttpSession sessao = login("ana.lima", "Ana#Rede1");
        mvc.perform(get("/api/me").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("USUARIO"))
                .andExpect(jsonPath("$.origem").value("DOMINIO"))
                .andExpect(jsonPath("$.nomeCompleto").value("Ana Lima"));
        mvc.perform(get("/api/patrimonios").session(sessao)).andExpect(status().isForbidden());
        mvc.perform(get("/api/frota/veiculos").session(sessao)).andExpect(status().isForbidden());

        // Também aceita DOMINIO\login e login@dominio, e o Basic do app.
        login("FPTO\\ana.lima", "Ana#Rede1");
        login("ana.lima@fpto.local", "Ana#Rede1");
        mvc.perform(get("/api/me").with(httpBasic("ana.lima", "Ana#Rede1"))).andExpect(status().isOk());

        Usuario u = usuarioRepo.findByLogin("ana.lima").orElseThrow();
        assertThat(u.getOrigem()).isEqualTo(OrigemUsuario.DOMINIO);
        assertThat(encoder.matches("Ana#Rede1", u.getSenhaHash())).isFalse(); // a senha da rede não é guardada
    }

    @Test
    void senhaErradaVaziaOuContaDesativadaNoAdSaoRecusadas() throws Exception {
        falhaLogin("ana.lima", "errada");
        // Bind LDAP com senha vazia é anônimo e "funciona": não pode virar login.
        falhaLogin("ana.lima", "");
        falhaLogin("maria.desativada", "Maria#Rede1");
        falhaLogin("ninguem", "qualquer");
        mvc.perform(get("/api/me").with(httpBasic("ana.lima", ""))).andExpect(status().isUnauthorized());
    }

    @Test
    void administradorCriaGruposEDefineOAcessoGravandoNoAd() throws Exception {
        MockHttpSession joao = login("joao.silva", "Joao#Rede1");
        mvc.perform(get("/api/me").session(joao)).andExpect(jsonPath("$.perfil").value("USUARIO"));

        mvc.perform(post("/api/admin/dominio/grupos").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.conectado").value(true))
                .andExpect(jsonPath("$.grupos.length()").value(7))
                .andExpect(jsonPath("$.grupos[?(@.nome == 'Patrimonio-Fiscal')].existe").value(true));
        assertThat(AD.getEntry("CN=Patrimonio-Administrador," + OU)).isNotNull();

        mvc.perform(get("/api/admin/dominio/usuarios").param("termo", "silva").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].login").value("joao.silva"))
                .andExpect(jsonPath("$[0].perfil").value("USUARIO"));

        mvc.perform(put("/api/admin/dominio/usuarios/joao.silva").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":\"FISCAL\",\"podeAbastecer\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("FISCAL"))
                .andExpect(jsonPath("$.podeAbastecer").value(true));
        assertThat(membros("Patrimonio-Fiscal")).contains("CN=Joao Silva," + USERS);
        assertThat(membros("Patrimonio-Abastecer")).contains("CN=Joao Silva," + USERS);

        // A sessão aberta com o acesso antigo é encerrada; no novo login vale o grupo do AD.
        mvc.perform(get("/api/me").session(joao)).andExpect(status().isUnauthorized());
        MockHttpSession joaoFiscal = login("joao.silva", "Joao#Rede1");
        mvc.perform(get("/api/me").session(joaoFiscal)).andExpect(jsonPath("$.perfil").value("FISCAL"))
                .andExpect(jsonPath("$.podeAbastecer").value(true));
        mvc.perform(get("/api/patrimonios").session(joaoFiscal)).andExpect(status().isOk());

        // Voltar a usuário padrão tira de todos os grupos do Patrimônio.
        mvc.perform(put("/api/admin/dominio/usuarios/joao.silva").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perfil\":null,\"podeAbastecer\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("USUARIO"));
        assertThat(membros("Patrimonio-Fiscal")).doesNotContain("CN=Joao Silva," + USERS);
        assertThat(membros("Patrimonio-Abastecer")).doesNotContain("CN=Joao Silva," + USERS);

        // Só administrador gerencia os grupos.
        mvc.perform(get("/api/admin/dominio/status").session(login("joao.silva", "Joao#Rede1"))).andExpect(status().isForbidden());
    }

    @Test
    void cadastroLocalAntigoPassaParaASenhaDaRedeSemPerderOPerfil() throws Exception {
        mvc.perform(post("/api/admin/dominio/grupos").with(admin())).andExpect(status().isOk());
        usuarioRepo.findByLogin("carlos.souza").orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Carlos (cadastro antigo)").login("carlos.souza").senhaHash(encoder.encode("senha-antiga"))
                .perfil(Perfil.MOTORISTA).ativo(true).build()));

        MockHttpSession carlos = login("carlos.souza", "Carlos#Rede1");
        mvc.perform(get("/api/me").session(carlos)).andExpect(jsonPath("$.perfil").value("MOTORISTA"))
                .andExpect(jsonPath("$.origem").value("DOMINIO"));
        assertThat(membros("Patrimonio-Motorista")).contains("CN=Carlos Souza," + USERS);
        // Depois da troca, a senha antiga do sistema não vale mais.
        falhaLogin("carlos.souza", "senha-antiga");
    }

    @Test
    void usuarioDaRedeTrocaASenhaNoWindowsENaoNoSistema() throws Exception {
        MockHttpSession pedro = login("pedro.rede", "Pedro#Rede1");
        mvc.perform(post("/api/me/senha").session(pedro).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"Pedro#Rede1\",\"novaSenha\":\"Outra#Senha2\",\"confirmacao\":\"Outra#Senha2\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void contaLocalSemContaNaRedeContinuaEntrandoPelaSenhaDoSistema() throws Exception {
        mvc.perform(get("/api/me").with(admin())).andExpect(status().isOk())
                .andExpect(jsonPath("$.origem").value("LOCAL"));
        falhaLogin("admin.ad", "senha-errada");
    }

    // =========================================================================

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return httpBasic("admin.ad", "Admin#Local1");
    }

    private MockHttpSession login(String login, String senha) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login.replace("\\", "\\\\") + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    private void falhaLogin(String login, String senha) throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static List<String> membros(String grupo) throws LDAPException {
        Entry e = AD.getEntry("CN=" + grupo + "," + OU);
        String[] m = e == null ? null : e.getAttributeValues("member");
        return m == null ? List.of() : Arrays.asList(m);
    }

    private static InMemoryDirectoryServer iniciarAd() {
        try {
            InMemoryDirectoryServerConfig cfg = new InMemoryDirectoryServerConfig(BASE);
            cfg.setSchema(null); // atributos do AD (sAMAccountName, member, groupType...) sem schema
            cfg.addAdditionalBindCredentials(SERVICO, "Servico#123");
            cfg.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("ldap", 0));
            InMemoryDirectoryServer s = new InMemoryDirectoryServer(cfg);
            s.add("dn: " + BASE, "objectClass: top", "objectClass: domain", "dc: fpto");
            s.add("dn: " + USERS, "objectClass: top", "objectClass: container", "cn: Users");
            s.add("dn: " + OU, "objectClass: top", "objectClass: organizationalUnit", "ou: Patrimonio");
            pessoa(s, "Ana Lima", "ana.lima", "Ana#Rede1", 512);
            pessoa(s, "Joao Silva", "joao.silva", "Joao#Rede1", 512);
            pessoa(s, "Carlos Souza", "carlos.souza", "Carlos#Rede1", 512);
            pessoa(s, "Pedro Rede", "pedro.rede", "Pedro#Rede1", 512);
            pessoa(s, "Maria Desativada", "maria.desativada", "Maria#Rede1", 514);
            // Conta de computador também é objectClass=user no AD: não pode aparecer nem logar.
            s.add("dn: CN=PC01," + USERS, "objectClass: top", "objectClass: user", "objectClass: computer",
                    "cn: PC01", "sAMAccountName: pc01$");
            s.startListening();
            return s;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void pessoa(InMemoryDirectoryServer s, String nome, String login, String senha, int uac) throws Exception {
        s.add("dn: CN=" + nome + "," + USERS, "objectClass: top", "objectClass: person", "objectClass: organizationalPerson",
                "objectClass: user", "cn: " + nome, "displayName: " + nome, "sAMAccountName: " + login,
                "userPrincipalName: " + login + "@fpto.local", "userPassword: " + senha, "userAccountControl: " + uac);
    }
}

package com.fundacao.gerenciador_patrimonial;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Autenticação da SPA React: login JSON em /api/auth/login abre sessão, /api/me responde
 * com a sessão, logout encerra, e as rotas do app (/app/...) são entregues sem autenticação
 * (a própria SPA decide o que mostrar).
 */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha=",
        "app.frota.transportes.url=",
        "app.frota.seed=false"
})
@AutoConfigureMockMvc
class SpaAuthFluxoTest {

    private MockMvc mvc;

    @Autowired WebApplicationContext ctx;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        usuarioRepo.findByLogin("spa.user").orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Usuária SPA").login("spa.user").senhaHash(encoder.encode("segredo-spa"))
                .perfil(Perfil.FISCAL).ativo(true).build()));
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    @Test
    void rotasDaSpaSaoEntreguesSemLogin() throws Exception {
        mvc.perform(get("/app")).andExpect(status().isOk()).andExpect(forwardedUrl("/app/index.html"));
        mvc.perform(get("/app/frota/12")).andExpect(status().isOk()).andExpect(forwardedUrl("/app/index.html"));
        mvc.perform(get("/app/levantamentos/3/locais/7")).andExpect(status().isOk()).andExpect(forwardedUrl("/app/index.html"));
        // sem sessão a API responde 401 puro (nunca redirect para /login)
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        // rotas antigas da interface Thymeleaf e a raiz levam para a SPA
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/app"));
        mvc.perform(get("/login")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/app/login"));
        mvc.perform(get("/frota")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/app"));
    }

    @Test
    void loginJsonAbreSessaoEDepoisLogoutEncerra() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"spa.user\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").exists());

        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"SPA.USER\",\"senha\":\"segredo-spa\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("spa.user"))
                .andExpect(jsonPath("$.perfil").value("FISCAL"))
                .andReturn();
        MockHttpSession sessao = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(sessao).isNotNull();

        mvc.perform(get("/api/me").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nomeCompleto").value("Usuária SPA"));
        mvc.perform(get("/api/me/resumo").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendenciasPatrimoniamento").isNumber());
        // regra de perfil espelhada na API: FISCAL não gerencia usuários
        mvc.perform(get("/api/usuarios").session(sessao)).andExpect(status().isForbidden());

        mvc.perform(post("/api/auth/logout").session(sessao)).andExpect(status().isNoContent());
        mvc.perform(get("/api/me").session(sessao)).andExpect(status().isUnauthorized());
    }
}

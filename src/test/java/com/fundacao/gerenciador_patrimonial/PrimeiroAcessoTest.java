package com.fundacao.gerenciador_patrimonial;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Primeiro acesso pela SPA: usuário criado pelo administrador entra com senha provisória,
 * {@code GET /api/me} avisa a SPA (que o leva à troca de senha), a troca recusa senha igual,
 * e depois a flag some. Redefinir a senha pelo admin volta a marcar como provisória.
 */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha=",
        "app.frota.transportes.url=",
        "app.frota.seed=false"
})
@AutoConfigureMockMvc
class PrimeiroAcessoTest {

    private MockMvc mvc;
    /** Sem usuário padrão: usado nas requisições que carregam a sessão do login real. */
    private MockMvc mvcSessao;

    @Autowired WebApplicationContext ctx;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        Usuario admin = usuarioRepo.findByLogin("admin.acesso").orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Admin Acesso").login("admin.acesso").senhaHash(encoder.encode("segredo-teste"))
                .perfil(Perfil.ADMINISTRADOR).ativo(true).build()));
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity())
                .defaultRequest(get("/").with(user(new UsuarioAutenticado(admin)))).build();
        mvcSessao = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    @Test
    void usuarioNovoPrecisaTrocarSenhaAntesDeNavegar() throws Exception {
        String login = "motorista." + UUID.randomUUID().toString().substring(0, 6);

        // 1) Admin cadastra com senha provisória (padrão do formulário da SPA)
        mvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto":"Motorista Novo","login":"%s","senha":"provisoria1",
                                 "perfil":"MOTORISTA","ativo":true,"exigirTrocaSenha":true}""".formatted(login)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senhaProvisoria").value(true));
        Usuario novo = usuarioRepo.findByLogin(login).orElseThrow();
        assertThat(novo.isSenhaProvisoria()).isTrue();

        // 2) Login de verdade pela SPA → a resposta já avisa que a senha é provisória
        MockHttpSession sessao = (MockHttpSession) mvcSessao.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"senha\":\"provisoria1\"}".formatted(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senhaProvisoria").value(true))
                .andReturn().getRequest().getSession(false);
        assertThat(sessao).isNotNull();
        mvcSessao.perform(get("/api/me").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.senhaProvisoria").value(true));

        // 3) Nova senha igual à provisória é recusada
        mvcSessao.perform(post("/api/me/senha").session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"provisoria1\",\"novaSenha\":\"provisoria1\",\"confirmacao\":\"provisoria1\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(usuarioRepo.findByLogin(login).orElseThrow().isSenhaProvisoria()).isTrue();

        // 4) Troca → flag liberada na sessão e no banco; motorista segue usando a frota
        mvcSessao.perform(post("/api/me/senha").session(sessao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senhaAtual\":\"provisoria1\",\"novaSenha\":\"minhaSenha2\",\"confirmacao\":\"minhaSenha2\"}"))
                .andExpect(status().is2xxSuccessful());
        assertThat(usuarioRepo.findByLogin(login).orElseThrow().isSenhaProvisoria()).isFalse();
        mvcSessao.perform(get("/api/me").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.senhaProvisoria").value(false));
        mvcSessao.perform(get("/api/frota/veiculos").session(sessao)).andExpect(status().isOk());

        // 5) Admin redefine a senha → volta a ser provisória
        mvc.perform(put("/api/usuarios/" + novo.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCompleto":"Motorista Novo","login":"%s","senha":"outra123",
                                 "perfil":"MOTORISTA","ativo":true,"exigirTrocaSenha":true}""".formatted(login)))
                .andExpect(status().isOk());
        assertThat(usuarioRepo.findByLogin(login).orElseThrow().isSenhaProvisoria()).isTrue();
    }
}

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Primeiro acesso: usuário criado pelo administrador entra com senha provisória,
 * é levado à troca de senha, não navega em mais nada até trocar, e depois segue normalmente.
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

        // 1) Admin cadastra com senha provisória (padrão do formulário)
        mvc.perform(post("/usuarios").with(csrf())
                        .param("nomeCompleto", "Motorista Novo").param("login", login).param("senha", "provisoria1")
                        .param("perfil", "MOTORISTA").param("ativo", "true").param("exigirTrocaSenha", "true"))
                .andExpect(status().is3xxRedirection());
        Usuario novo = usuarioRepo.findByLogin(login).orElseThrow();
        assertThat(novo.isSenhaProvisoria()).isTrue();

        // 2) Login de verdade pelo formulário → cai na troca de senha
        var sessao = mvcSessao.perform(formLogin("/login").user(login).password("provisoria1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/minha-conta/senha?obrigatoria"))
                .andReturn().getRequest().getSession();

        // 3) Qualquer outra página redireciona enquanto a senha for provisória
        mvcSessao.perform(get("/frota").session((org.springframework.mock.web.MockHttpSession) sessao))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/minha-conta/senha?obrigatoria"));
        mvcSessao.perform(get("/minha-conta/senha").session((org.springframework.mock.web.MockHttpSession) sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Primeiro acesso")));

        // Nova senha igual à provisória é recusada
        mvcSessao.perform(post("/minha-conta/senha").with(csrf()).session((org.springframework.mock.web.MockHttpSession) sessao)
                        .param("senhaAtual", "provisoria1").param("novaSenha", "provisoria1").param("confirmacao", "provisoria1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("diferente da senha atual")));

        // 4) Troca → liberado, motorista cai na frota
        mvcSessao.perform(post("/minha-conta/senha").with(csrf()).session((org.springframework.mock.web.MockHttpSession) sessao)
                        .param("senhaAtual", "provisoria1").param("novaSenha", "minhaSenha2").param("confirmacao", "minhaSenha2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/frota"));
        assertThat(usuarioRepo.findByLogin(login).orElseThrow().isSenhaProvisoria()).isFalse();
        mvcSessao.perform(get("/frota").session((org.springframework.mock.web.MockHttpSession) sessao)).andExpect(status().isOk());

        // 5) Admin redefine a senha → volta a ser provisória
        mvc.perform(post("/usuarios/" + novo.getId()).with(csrf())
                        .param("nomeCompleto", "Motorista Novo").param("login", login).param("senha", "outra123")
                        .param("perfil", "MOTORISTA").param("ativo", "true").param("exigirTrocaSenha", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(usuarioRepo.findByLogin(login).orElseThrow().isSenhaProvisoria()).isTrue();
    }
}

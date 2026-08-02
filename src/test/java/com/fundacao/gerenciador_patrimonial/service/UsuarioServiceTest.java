package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.dto.request.TrocarSenhaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.UsuarioRequest;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Gestão de usuários: unicidade de login, senha obrigatória e troca de senha. */
class UsuarioServiceTest {

    private UsuarioRepository repo;
    private PasswordEncoder encoder;

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        repo    = mock(UsuarioRepository.class);
        encoder = mock(PasswordEncoder.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        CacheManager cacheManager = mock(CacheManager.class); // getCache → null: evict vira no-op
        service = new UsuarioService(repo, encoder, auditoria, cacheManager);
    }

    private static UsuarioRequest request(String login, String senha) {
        return new UsuarioRequest("Fulano de Tal", login, senha, Perfil.FISCAL, true);
    }

    @Test
    @DisplayName("criar: sem senha → RegraDeNegocioException")
    void criarSemSenha() {
        assertThatThrownBy(() -> service.criar(request("fulano", null)))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.criar(request("fulano", "  ")))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("criar: login já em uso → RegraDeNegocioException")
    void criarLoginDuplicado() {
        when(repo.existsByLogin("fulano")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(request("fulano", "senha123")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("fulano");
    }

    @Test
    @DisplayName("criar: normaliza login para minúsculas e armazena hash, nunca a senha")
    void criarComSucesso() {
        when(repo.existsByLogin(any())).thenReturn(false);
        when(encoder.encode("senha123")).thenReturn("$hash$");
        when(repo.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.criar(request("  FuLaNo ".trim(), "senha123"));

        assertThat(resp.login()).isEqualTo("fulano");
        verify(repo).save(any(Usuario.class));
    }

    @Test
    @DisplayName("trocarSenha: confirmação divergente → erro antes de consultar o banco")
    void trocarSenhaConfirmacaoDivergente() {
        assertThatThrownBy(() -> service.trocarSenha("fulano",
                new TrocarSenhaRequest("atual", "nova123", "outra")))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(repo, never()).findByLogin(any());
    }

    @Test
    @DisplayName("trocarSenha: senha atual incorreta → erro sem alterar o hash")
    void trocarSenhaAtualIncorreta() {
        Usuario u = Usuario.builder().id(1L).login("fulano").senhaHash("$velho$").build();
        when(repo.findByLogin("fulano")).thenReturn(Optional.of(u));
        when(encoder.matches("errada", "$velho$")).thenReturn(false);

        assertThatThrownBy(() -> service.trocarSenha("fulano",
                new TrocarSenhaRequest("errada", "nova123", "nova123")))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(u.getSenhaHash()).isEqualTo("$velho$");
    }

    @Test
    @DisplayName("trocarSenha: fluxo feliz re-encoda a nova senha")
    void trocarSenhaComSucesso() {
        Usuario u = Usuario.builder().id(1L).login("fulano").senhaHash("$velho$").build();
        when(repo.findByLogin("fulano")).thenReturn(Optional.of(u));
        when(encoder.matches("atual", "$velho$")).thenReturn(true);
        when(encoder.encode("nova123")).thenReturn("$novo$");

        service.trocarSenha("fulano", new TrocarSenhaRequest("atual", "nova123", "nova123"));

        assertThat(u.getSenhaHash()).isEqualTo("$novo$");
    }

    @Test
    @DisplayName("inativar: seta ativo=false preservando o registro")
    void inativar() {
        Usuario u = Usuario.builder().id(1L).login("fulano").ativo(true).build();
        when(repo.findById(1L)).thenReturn(Optional.of(u));

        service.inativar(1L);

        assertThat(u.isAtivo()).isFalse();
        verify(repo, never()).delete(any());
    }
}

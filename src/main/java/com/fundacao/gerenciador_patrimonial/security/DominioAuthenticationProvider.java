package com.fundacao.gerenciador_patrimonial.security;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.OrigemUsuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import com.fundacao.gerenciador_patrimonial.service.dominio.DiretorioAd;
import com.fundacao.gerenciador_patrimonial.service.dominio.DiretorioAd.DiretorioIndisponivelException;
import com.fundacao.gerenciador_patrimonial.service.dominio.DiretorioAd.ResultadoLogin;
import com.fundacao.gerenciador_patrimonial.service.dominio.DominioUsuarioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import java.util.Optional;

/**
 * Login com o usuário e a senha da rede da Fundação (AD FPTO.LOCAL). Vale para o login da
 * SPA ({@code POST /api/auth/login}) e para o HTTP Basic do app Android.
 *
 * <p>Fica antes do login local no {@code ProviderManager}:</p>
 * <ul>
 *   <li>AD desligado, login que não é de rede ou conta técnica (INTEGRACAO) → passa para o login local.</li>
 *   <li>Senha conferida no AD → cadastro sincronizado ({@link DominioUsuarioService}) e login feito.</li>
 *   <li>Senha recusada → conta LOCAL ainda tenta a senha do sistema; conta da rede é recusada.</li>
 *   <li>AD fora do ar → conta LOCAL entra pela senha do sistema; conta da rede recebe 503.</li>
 * </ul>
 */
@Slf4j
public class DominioAuthenticationProvider implements AuthenticationProvider {

    private final DiretorioAd diretorio;
    private final DominioUsuarioService dominioUsuarios;
    private final UsuarioRepository usuarioRepo;
    private final TentativasLoginService tentativas;

    public DominioAuthenticationProvider(DiretorioAd diretorio, DominioUsuarioService dominioUsuarios,
                                         UsuarioRepository usuarioRepo, TentativasLoginService tentativas) {
        this.diretorio = diretorio;
        this.dominioUsuarios = dominioUsuarios;
        this.usuarioRepo = usuarioRepo;
        this.tentativas = tentativas;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!diretorio.habilitado()) return null;
        String login = diretorio.normalizarLogin(authentication.getName());
        if (login == null) return null;
        Object credencial = authentication.getCredentials();
        String senha = credencial == null ? "" : credencial.toString();

        Usuario local = usuarioRepo.findByLogin(login).orElse(null);
        if (local != null && local.getPerfil() == Perfil.INTEGRACAO) return null; // conta técnica: só senha local
        boolean contaLocal = local != null && local.getOrigem() == OrigemUsuario.LOCAL;

        if (tentativas.bloqueado(login) || tentativas.bloqueado(authentication.getName())) {
            throw new LockedException("Muitas tentativas de login.");
        }

        Optional<ResultadoLogin> resultado;
        try {
            resultado = diretorio.autenticar(login, senha);
        } catch (DiretorioIndisponivelException e) {
            if (contaLocal) return null;
            throw new InternalAuthenticationServiceException(e.getMessage(), e);
        }
        if (resultado.isEmpty()) {
            if (contaLocal) return null;
            throw new BadCredentialsException("Usuário ou senha inválidos.");
        }

        Usuario usuario = dominioUsuarios.sincronizarNoLogin(resultado.get());
        if (!usuario.isAtivo()) throw new DisabledException("Usuário desativado no Patrimônio.");
        UsuarioAutenticado principal = new UsuarioAutenticado(usuario);
        log.debug("Login pela rede: {} ({})", usuario.getLogin(), usuario.getPerfil());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}

package com.fundacao.gerenciador_patrimonial.security;

import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega o usuário a partir da tabela {@code usuario} para o fluxo
 * de autenticação do Spring Security.
 *
 * <p>Usuários inativos ficam logicamente fora do login: o
 * {@link UsuarioAutenticado#isEnabled()} retorna {@code false} e a
 * autenticação é negada automaticamente. Logins com tentativas de senha
 * demais ficam bloqueados por um intervalo ({@link TentativasLoginService}).</p>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepo;
    private final TentativasLoginService tentativas;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        boolean bloqueado = tentativas.bloqueado(login);
        return usuarioRepo.findByLogin(login)
                .map(u -> new UsuarioAutenticado(u, bloqueado)) // bloqueado → LockedException antes de testar a senha
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + login));
    }
}

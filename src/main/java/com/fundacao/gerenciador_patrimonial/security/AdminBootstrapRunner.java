package com.fundacao.gerenciador_patrimonial.security;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria o usuário administrador inicial se ainda não existir nenhum.
 *
 * <p>Configurável via {@code application.yml}:</p>
 * <pre>
 *   app.admin.login: admin
 *   app.admin.senha: (defina em produção!)
 *   app.admin.nome: Administrador
 * </pre>
 *
 * <p>A senha padrão {@code trocar@123} só é aplicada se nenhum admin existir
 * e nenhuma senha tiver sido configurada. Em produção, SEMPRE defina
 * a variável de ambiente/secret correspondente.</p>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner {

    @Bean
    @Order(1) // Garante que o admin seja criado ANTES do seed de patrimônios (@Order 2)
    ApplicationRunner criarAdminInicial(UsuarioRepository repo,
                                        PasswordEncoder encoder,
                                        @Value("${app.admin.login:admin}") String login,
                                        @Value("${app.admin.senha:trocar@123}") String senha,
                                        @Value("${app.admin.nome:Administrador}") String nome) {
        return args -> {
            // Se já existe QUALQUER administrador ativo, não cria nada.
            boolean existeAlgumAdmin = repo.findAll().stream()
                    .anyMatch(u -> u.getPerfil() == Perfil.ADMINISTRADOR && u.isAtivo());
            if (existeAlgumAdmin) {
                log.debug("Administrador já existe — seed ignorado.");
                return;
            }

            if (repo.existsByLogin(login)) {
                log.warn("Login '{}' já existe mas sem perfil ADMINISTRADOR — ignorando seed.", login);
                return;
            }

            Usuario admin = Usuario.builder()
                    .nomeCompleto(nome)
                    .login(login.toLowerCase())
                    .senhaHash(encoder.encode(senha))
                    .perfil(Perfil.ADMINISTRADOR)
                    .ativo(true)
                    .build();
            repo.save(admin);

            log.warn("=============================================================");
            log.warn("Administrador inicial criado: login='{}'", login);
            log.warn("Altere a senha imediatamente após o primeiro login!");
            log.warn("=============================================================");
        };
    }
}

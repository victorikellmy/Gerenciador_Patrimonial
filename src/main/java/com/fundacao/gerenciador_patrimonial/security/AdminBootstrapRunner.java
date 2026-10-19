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

import java.security.SecureRandom;

/**
 * Cria o usuário administrador inicial se ainda não existir nenhum administrador ativo.
 *
 * <p>Configurável via variáveis de ambiente / {@code application.yml}:</p>
 * <pre>
 *   app.admin.login: admin          (APP_ADMIN_LOGIN)
 *   app.admin.senha: ...            (APP_ADMIN_SENHA)
 *   app.admin.nome:  Administrador  (APP_ADMIN_NOME)
 * </pre>
 *
 * <p>Não há senha padrão no código. Sem {@code app.admin.senha}, o boot gera uma senha
 * aleatória e a escreve UMA vez no log. Em qualquer caso o admin nasce com senha
 * provisória: a API só libera {@code /api/me/**} até a troca (ver {@link SessaoUsuarioFilter}).</p>
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner {

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    @Bean
    @Order(1) // Garante que o admin seja criado ANTES do seed de patrimônios (@Order 2)
    ApplicationRunner criarAdminInicial(UsuarioRepository repo,
                                        PasswordEncoder encoder,
                                        @Value("${app.admin.login:admin}") String login,
                                        @Value("${app.admin.senha:}") String senhaConfigurada,
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
                log.warn("Login '{}' já existe mas sem perfil ADMINISTRADOR ativo — ignorando seed.", login);
                return;
            }

            boolean gerada = senhaConfigurada == null || senhaConfigurada.isBlank();
            String senha = gerada ? senhaAleatoria() : senhaConfigurada;

            Usuario admin = Usuario.builder()
                    .nomeCompleto(nome)
                    .login(login.toLowerCase())
                    .senhaHash(encoder.encode(senha))
                    .perfil(Perfil.ADMINISTRADOR)
                    .ativo(true)
                    .senhaProvisoria(true) // a API só libera a troca de senha até o primeiro acesso
                    .build();
            repo.save(admin);

            log.warn("=============================================================");
            log.warn("Administrador inicial criado: login='{}'", login);
            if (gerada) {
                log.warn("APP_ADMIN_SENHA não definida: senha provisória gerada = {}", senha);
                log.warn("Ela só é exibida agora. Troque-a no primeiro acesso.");
            } else {
                log.warn("Senha definida por APP_ADMIN_SENHA. Troque-a no primeiro acesso.");
            }
            log.warn("=============================================================");
        };
    }

    private static String senhaAleatoria() {
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 20; i++) sb.append(ALFABETO.charAt(rnd.nextInt(ALFABETO.length())));
        return sb.toString();
    }
}

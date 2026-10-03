package com.fundacao.gerenciador_patrimonial.security;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cria (ou atualiza a senha de) o usuário técnico usado pelo Almoxarifado para
 * chamar {@code POST /api/integracao/almoxarifado/recebimentos}.
 *
 * <p>Só age quando a senha vier configurada:</p>
 * <pre>
 *   INTEGRACAO_ALMOXARIFADO_SENHA=...        (obrigatória para ativar)
 *   INTEGRACAO_ALMOXARIFADO_LOGIN=integracao.almoxarifado   (opcional)
 * </pre>
 * <p>No Almoxarifado, configure os mesmos valores em
 * {@code PATRIMONIO_USUARIO} / {@code PATRIMONIO_SENHA}.</p>
 *
 * <p>Se o usuário já existir, a senha é re-aplicada (permite rotação por
 * variável de ambiente) e o perfil é forçado para {@link Perfil#INTEGRACAO}.</p>
 */
@Configuration
@Slf4j
public class IntegracaoBootstrapRunner {

    @Bean
    @Order(1)
    ApplicationRunner criarUsuarioIntegracao(UsuarioRepository repo,
                                             PasswordEncoder encoder,
                                             @Value("${app.integracao.almoxarifado.login:integracao.almoxarifado}") String login,
                                             @Value("${app.integracao.almoxarifado.senha:}") String senha) {
        return args -> {
            if (senha == null || senha.isBlank()) {
                log.info("Usuário de integração do Almoxarifado não configurado " +
                         "(defina INTEGRACAO_ALMOXARIFADO_SENHA para ativar).");
                return;
            }
            String loginNorm = login.trim().toLowerCase();
            Usuario u = repo.findByLogin(loginNorm).orElse(null);
            if (u == null) {
                u = Usuario.builder()
                        .nomeCompleto("Integração Almoxarifado")
                        .login(loginNorm)
                        .senhaHash(encoder.encode(senha))
                        .perfil(Perfil.INTEGRACAO)
                        .ativo(true)
                        .build();
                repo.save(u);
                log.warn("Usuário de integração criado: login='{}' (perfil INTEGRACAO).", loginNorm);
                return;
            }
            if (!encoder.matches(senha, u.getSenhaHash()) || u.getPerfil() != Perfil.INTEGRACAO || !u.isAtivo()) {
                u.setSenhaHash(encoder.encode(senha));
                u.setPerfil(Perfil.INTEGRACAO);
                u.setAtivo(true);
                repo.save(u);
                log.warn("Usuário de integração '{}' atualizado a partir das variáveis de ambiente.", loginNorm);
            }
        };
    }
}

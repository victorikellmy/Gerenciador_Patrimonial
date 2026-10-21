package com.fundacao.gerenciador_patrimonial.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Configuração central do Spring Security.
 *
 * <p>Estratégia:</p>
 * <ul>
 *   <li>SPA React em /app → pública; autentica por POST /api/auth/login (sessão por cookie)</li>
 *   <li>Endpoints /api → sessão da SPA ou HTTP Basic (app Android/scripts), sem CSRF</li>
 *   <li>Recursos estáticos (/css, /images) e H2-console (dev) → públicos; o resto é negado</li>
 * </ul>
 *
 * <p>As regras de autorização granular nos controllers são feitas via
 * {@code @PreAuthorize} quando necessário — aqui ficam apenas as amarrações
 * por caminho/método.</p>
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository usuarioRepo;

    private static final String[] LEITURA = {"ADMINISTRADOR", "FISCAL", "GERENTE", "PRESIDENTE", "FINANCEIRO"};
    private static final String[] TODOS_HUMANOS = {"ADMINISTRADOR", "FISCAL", "GERENTE", "PRESIDENTE", "FINANCEIRO", "MOTORISTA"};

    /** BCrypt com cost 10 (default) — resistente a brute force. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Exposto para o login por JSON da SPA ({@code POST /api/auth/login}). */
    @Bean
    public org.springframework.security.authentication.AuthenticationManager authenticationManager(
            org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // =========================================================================
    // Filter chain #1 — API REST (/api/**)
    // =========================================================================
    //
    // Ordem baixa (1) para ser avaliada ANTES da chain da SPA. Aceita a sessão
    // aberta pela SPA (POST /api/auth/login) ou HTTP Basic (app Android, curl).
    //
    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                // CSRF desligado: o app Android usa Basic sem sessão e a SPA usa sessão com cookie
                // SameSite=Lax (server.servlet.session.cookie.same-site), o que bloqueia POST cross-site.
                .csrf(AbstractHttpConfigurer::disable)
                // IF_REQUIRED: a SPA React (/app) loga por POST /api/auth/login e segue na sessão;
                // chamadas com Basic (Android) continuam sem criar sessão útil.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                        // Integração com sistemas externos (Almoxarifado): usuário técnico INTEGRACAO
                        // ou um ADMINISTRADOR (testes manuais via curl).
                        .requestMatchers("/api/integracao/**").hasAnyRole("ADMINISTRADOR", "INTEGRACAO")
                        // --- Frota ---
                        .requestMatchers(HttpMethod.DELETE, "/api/frota/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/frota/importar-transportes", "/api/frota/veiculos/*/ativo").hasRole("ADMINISTRADOR")
                        // Abastecimento: quem lança na frota + quem tem a permissão extra (Usuario.podeAbastecer).
                        .requestMatchers(HttpMethod.POST, "/api/frota/veiculos/*/abastecimentos").hasAnyAuthority(
                                "ROLE_ADMINISTRADOR", "ROLE_FISCAL", "ROLE_PRESIDENTE", "ROLE_MOTORISTA", UsuarioAutenticado.FROTA_ABASTECER)
                        // Seguro e sinistros: só quem gere a frota cadastra (PUT/DELETE já seguem as regras acima).
                        .requestMatchers(HttpMethod.POST, "/api/frota/seguros", "/api/frota/seguros/**",
                                                          "/api/frota/sinistros", "/api/frota/sinistros/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "PRESIDENTE")
                        .requestMatchers(HttpMethod.POST, "/api/frota/veiculos", "/api/frota/veiculos/*/manutencao-status").hasAnyRole("ADMINISTRADOR", "FISCAL", "PRESIDENTE")
                        .requestMatchers(HttpMethod.PUT, "/api/frota/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "PRESIDENTE")
                        // Trocar a NF de um lançamento existente é edição: só ADMIN/FISCAL/PRESIDENTE. O motorista
                        // anexa a NF junto com o cadastro (multipart em /veiculos/{id}/abastecimentos|manutencoes).
                        .requestMatchers(HttpMethod.POST, "/api/frota/abastecimentos/*/nota-fiscal",
                                                          "/api/frota/manutencoes/*/nota-fiscal").hasAnyRole("ADMINISTRADOR", "FISCAL", "PRESIDENTE")
                        // Painel gerencial: qualquer método (inclusive HEAD) só para quem vê a gestão.
                        .requestMatchers("/api/frota/gestao", "/api/frota/gestao/**", "/api/frota/export/gestao").hasAnyRole("ADMINISTRADOR", "FISCAL", "GERENTE", "PRESIDENTE")
                        // Patrimônios ainda sem ficha de veículo: só para quem cadastra veículos.
                        .requestMatchers("/api/frota/patrimonios-disponiveis").hasAnyRole("ADMINISTRADOR", "FISCAL", "PRESIDENTE")
                        // --- Diárias: abrir/editar ADMIN e FISCAL; confirmar ADMIN; deferir/indeferir PRESIDENTE; pagar FINANCEIRO ---
                        .requestMatchers(HttpMethod.POST, "/api/diarias/*/confirmar", "/api/diarias/niveis/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/diarias/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/diarias/*/deferir", "/api/diarias/*/indeferir").hasAnyRole("ADMINISTRADOR", "PRESIDENTE")
                        .requestMatchers(HttpMethod.POST, "/api/diarias/*/pagar").hasAnyRole("ADMINISTRADOR", "FINANCEIRO")
                        .requestMatchers(HttpMethod.POST, "/api/diarias", "/api/diarias/profissionais/**").hasAnyRole("ADMINISTRADOR", "FISCAL")
                        .requestMatchers(HttpMethod.PUT, "/api/diarias/**").hasAnyRole("ADMINISTRADOR", "FISCAL")
                        // Cadastro de profissionais (CPF, RG, dados bancários) e valores para o formulário:
                        // só quem cadastra diárias. Os demais perfis veem os dados dentro de cada diária.
                        .requestMatchers("/api/diarias/profissionais", "/api/diarias/profissionais/**",
                                         "/api/diarias/valores/**").hasAnyRole("ADMINISTRADOR", "FISCAL")
                        .requestMatchers(HttpMethod.GET, "/api/diarias/**").hasAnyRole(LEITURA)
                        // --- Escrita/alteração exige ADMINISTRADOR ---
                        .requestMatchers(HttpMethod.POST, "/api/importacao/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/usuarios/**", "/api/admin/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/levantamentos").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/levantamentos/*/concluir",
                                                          "/api/levantamentos/*/reabrir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/pendencias/*/descartar").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/patrimonios/*/baixa").hasRole("ADMINISTRADOR")
                        // --- Leitura: GERENTE/PRESIDENTE/FINANCEIRO consultam; MOTORISTA só a frota ---
                        .requestMatchers(HttpMethod.GET, "/api/frota/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "GERENTE", "PRESIDENTE", "MOTORISTA")
                        .requestMatchers(HttpMethod.GET, "/api/relatorios/**", "/api/dashboard/**", "/api/patrimonios/**",
                                                         "/api/catalogos/**", "/api/lotacoes/**", "/api/responsaveis/**",
                                                         "/api/anexos/**").hasAnyRole(LEITURA)
                        .requestMatchers("/api/me/**", "/api/auth/**").hasAnyRole(TODOS_HUMANOS)
                        .requestMatchers("/api/frota/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "PRESIDENTE", "MOTORISTA")
                        // Restante da API: operadores humanos — o perfil INTEGRACAO fica de fora.
                        .anyRequest().hasAnyRole("ADMINISTRADOR", "FISCAL")
                )
                // Clientes REST precisam de 401/403 puros — nunca redirect.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> res.sendError(HttpStatus.FORBIDDEN.value())))
                .httpBasic(basic -> basic.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // Revalida o usuário a cada requisição (sessão revogada ao desativar/mudar perfil/senha)
                // e barra no servidor quem ainda está com senha provisória.
                .addFilterAfter(new SessaoUsuarioFilter(usuarioRepo),
                        org.springframework.security.web.authentication.www.BasicAuthenticationFilter.class);
        return http.build();
    }

    // =========================================================================
    // Filter chain #2 — SPA React e recursos estáticos
    // =========================================================================
    //
    // A interface é a SPA em /app (pública: a própria SPA decide o que mostrar e
    // usa /api/auth/login). Não há mais telas renderizadas no servidor: "/" e
    // "/login" redirecionam para a SPA (SpaController). Qualquer outra rota fora
    // de /api e /app é negada.
    //
    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index", "/index.html", "/login", "/logout",
                                "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico", "/error",
                                "/app", "/app/**"
                        ).permitAll()
                        // Downloads públicos (APK do app Android): só leitura de arquivos.
                        .requestMatchers(HttpMethod.GET, "/downloads/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/downloads/**").permitAll()
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")).permitAll()
                        .anyRequest().denyAll()
                )
                // Só há GETs de arquivos estáticos nesta cadeia; sem formulários não há o que proteger com CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // Rota antiga (/frota, /diarias...) ou desconhecida: leva para a SPA em vez de página de erro.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> res.sendRedirect(req.getContextPath() + "/app"))
                        .accessDeniedHandler((req, res, ex) -> res.sendRedirect(req.getContextPath() + "/app")))
                // H2 console usa frames — liberar apenas em mesmo origem
                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        return http.build();
    }
}

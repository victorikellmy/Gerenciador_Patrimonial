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
 *   <li>Interface web → sessão + form login + CSRF habilitado</li>
 *   <li>Endpoints /api → HTTP Basic + stateless (CSRF desabilitado apenas para /api)</li>
 *   <li>Recursos estáticos (/css, /js) e H2-console (dev) → públicos</li>
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

    /** BCrypt com cost 10 (default) — resistente a brute force. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // =========================================================================
    // Filter chain #1 — API REST (/api/**)
    // =========================================================================
    //
    // Ordem baixa (0) para ser avaliada ANTES da chain web. Usa HTTP Basic,
    // sem sessão e sem CSRF (padrão para APIs consumidas por scripts/curl).
    //
    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Integração com sistemas externos (Almoxarifado): usuário técnico INTEGRACAO
                        // ou um ADMINISTRADOR (testes manuais via curl).
                        .requestMatchers("/api/integracao/**").hasAnyRole("ADMINISTRADOR", "INTEGRACAO")
                        // Escrita/alteração exige ADMINISTRADOR (espelha as restrições da interface web)
                        .requestMatchers(HttpMethod.POST, "/api/importacao/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/usuarios/**", "/api/admin/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/levantamentos").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/levantamentos/*/concluir",
                                                          "/api/levantamentos/*/reabrir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/pendencias/*/descartar").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/patrimonios/*/baixa").hasRole("ADMINISTRADOR")
                        // Frota (app Android): motoristas também lançam
                        // GERENTE lê frota, relatórios, dashboard e patrimônios pela API; MOTORISTA lê e lança na frota
                        .requestMatchers(HttpMethod.GET, "/api/frota/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "GERENTE", "MOTORISTA")
                        .requestMatchers(HttpMethod.GET, "/api/relatorios/**", "/api/dashboard/**",
                                                         "/api/patrimonios/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "GERENTE")
                        .requestMatchers("/api/me/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "MOTORISTA", "GERENTE", "PRESIDENTE", "FINANCEIRO")
                        .requestMatchers("/api/frota/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "MOTORISTA")
                        // Restante da API: operadores humanos — o perfil INTEGRACAO fica de fora.
                        .anyRequest().hasAnyRole("ADMINISTRADOR", "FISCAL")
                )
                // Cliente REST (app Android) precisa de 401/403 puros — nunca redirect para /login.
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> res.sendError(HttpStatus.FORBIDDEN.value())))
                .httpBasic(basic -> basic.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    // =========================================================================
    // Filter chain #2 — Interface web
    // =========================================================================
    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // --- recursos estáticos e públicos ---
                        .requestMatchers(
                                "/login", "/css/**", "/js/**", "/images/**",
                                "/webjars/**", "/favicon.ico"
                        ).permitAll()
                        // H2 console (dev)
                        .requestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")).permitAll()

                        // --- áreas restritas ao ADMINISTRADOR ---
                        .requestMatchers("/usuarios/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/importacao/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/patrimonios/*/excluir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/patrimonios/*/baixa").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/patrimonios/anexos/*/excluir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/lotacoes/*/excluir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/responsaveis/*/inativar").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/pendencias/*/descartar").hasRole("ADMINISTRADOR")
                        // Levantamento patrimonial: abrir/concluir/reabrir só ADMIN; conferir é de todos os operadores
                        .requestMatchers(HttpMethod.POST, "/levantamentos").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/levantamentos/*/concluir",
                                                          "/levantamentos/*/reabrir").hasRole("ADMINISTRADOR")

                        // --- Frota: motoristas lançam; editar/cadastrar veículo é de ADMIN/FISCAL; excluir só ADMIN ---
                        .requestMatchers(HttpMethod.POST, "/frota/*/*/excluir", "/frota/importar-transportes",
                                                          "/frota/*/ativo").hasRole("ADMINISTRADOR")
                        .requestMatchers("/frota/novo", "/frota/*/editar", "/frota/*/*/editar").hasAnyRole("ADMINISTRADOR", "FISCAL")
                        .requestMatchers(HttpMethod.POST, "/frota", "/frota/{id:\\d+}", "/frota/*/manutencao-status",
                                                          "/frota/abastecimentos/{id:\\d+}",
                                                          "/frota/manutencoes/{id:\\d+}",
                                                          "/frota/deslocamentos/{id:\\d+}").hasAnyRole("ADMINISTRADOR", "FISCAL")
                        // --- Diárias: abrir/editar ADMIN e FISCAL; confirmar ADMIN; deferir/indeferir PRESIDENTE; pagar FINANCEIRO ---
                        .requestMatchers(HttpMethod.POST, "/diarias/*/confirmar", "/diarias/*/excluir",
                                                          "/diarias/niveis/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/diarias/*/deferir", "/diarias/*/indeferir").hasAnyRole("ADMINISTRADOR", "PRESIDENTE")
                        .requestMatchers(HttpMethod.POST, "/diarias/*/pagar").hasAnyRole("ADMINISTRADOR", "FINANCEIRO")
                        .requestMatchers("/diarias/nova", "/diarias/*/editar", "/diarias/profissionais/**").hasAnyRole("ADMINISTRADOR", "FISCAL")
                        .requestMatchers(HttpMethod.POST, "/diarias", "/diarias/{id:\\d+}").hasAnyRole("ADMINISTRADOR", "FISCAL")
                        .requestMatchers(HttpMethod.GET, "/diarias/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "PRESIDENTE", "FINANCEIRO", "GERENTE")
                        // --- Cautelas: motoristas registram; excluir só ADMIN ---
                        .requestMatchers(HttpMethod.POST, "/frota/cautelas/*/excluir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/frota/cautelas", "/frota/cautelas/*/termo", "/frota/titulares")
                                .hasAnyRole("ADMINISTRADOR", "FISCAL", "GERENTE", "MOTORISTA")
                        // --- GERENTE: somente leitura (painel gerencial, listas, relatórios, dashboard) ---
                        .requestMatchers(HttpMethod.GET, "/frota/gestao", "/frota/gestao/download").hasAnyRole("ADMINISTRADOR", "FISCAL", "GERENTE")
                        .requestMatchers(HttpMethod.GET, "/frota", "/frota/{id:\\d+}", "/frota/{id:\\d+}/historico/download",
                                                         "/frota/abastecimentos", "/frota/manutencoes", "/frota/deslocamentos",
                                                         "/frota/*/download", "/frota/*/*/nota-fiscal",
                                                         "/minha-conta/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "GERENTE", "MOTORISTA")
                        .requestMatchers(HttpMethod.GET, "/", "/relatorios/**", "/patrimonios",
                                                         "/patrimonios/*/historico").hasAnyRole("ADMINISTRADOR", "FISCAL", "GERENTE", "PRESIDENTE", "FINANCEIRO")
                        .requestMatchers("/minha-conta/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "GERENTE", "MOTORISTA", "PRESIDENTE", "FINANCEIRO")
                        .requestMatchers("/frota/**", "/minha-conta/**").hasAnyRole("ADMINISTRADOR", "FISCAL", "MOTORISTA")

                        // --- demais (inclui /pendencias/**) → operadores humanos ---
                        // O perfil INTEGRACAO é só para a API; não navega na interface web.
                        .anyRequest().hasAnyRole("ADMINISTRADOR", "FISCAL")
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        // Motorista cai direto na frota (não tem acesso ao dashboard).
                        .successHandler((req, res, auth) -> {
                            boolean motorista = auth.getAuthorities().stream()
                                    .anyMatch(a -> "ROLE_MOTORISTA".equals(a.getAuthority()));
                            boolean gerente = auth.getAuthorities().stream()
                                    .anyMatch(a -> "ROLE_GERENTE".equals(a.getAuthority()));
                            boolean diarias = auth.getAuthorities().stream()
                                    .anyMatch(a -> "ROLE_PRESIDENTE".equals(a.getAuthority()) || "ROLE_FINANCEIRO".equals(a.getAuthority()));
                            res.sendRedirect(req.getContextPath()
                                    + (motorista ? "/frota" : gerente ? "/frota/gestao" : diarias ? "/diarias" : "/"));
                        })
                        .failureUrl("/login?erro")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?desconectado")
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                // H2 console usa frames — liberar apenas em mesmo origem
                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                // CSRF habilitado por padrão, mas desabilitado no H2 console (dev)
                .csrf(c -> c.ignoringRequestMatchers(AntPathRequestMatcher.antMatcher("/h2-console/**")));
        return http.build();
    }
}

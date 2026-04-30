package com.fundacao.gerenciador_patrimonial.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
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
                        // Escrita/alteração exige ADMINISTRADOR
                        .requestMatchers(HttpMethod.POST, "/api/importacao/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMINISTRADOR")
                        .anyRequest().authenticated()
                )
                .httpBasic(org.springframework.security.config.Customizer.withDefaults());
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

                        // --- demais → autenticado ---
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
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

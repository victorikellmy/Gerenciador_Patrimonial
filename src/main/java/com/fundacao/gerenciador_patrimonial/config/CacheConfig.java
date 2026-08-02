package com.fundacao.gerenciador_patrimonial.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache em memória (Caffeine) para resultados que são recalculados a cada
 * request mas mudam raramente.
 *
 * <p>Estratégia: TTL curto (60s) em vez de eviction explícita nas escritas —
 * simples, sem acoplamento dos services de escrita ao cache, e o dado fica
 * no máximo 1 minuto defasado. Caches:</p>
 * <ul>
 *   <li>{@code dashboard} — métricas agregadas da home (inclui o loop de
 *       depreciação sobre todos os ativos, que era executado a cada F5);</li>
 *   <li>{@code categorias-distintas} / {@code upms-distintas} — listas de
 *       filtro consultadas em todo GET de /patrimonios;</li>
 *   <li>{@code user-details} — usuário autenticado da API stateless (HTTP
 *       Basic revalida a cada request; o cache elimina o SELECT por request,
 *       com evict imediato quando o usuário é alterado/inativado).</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_USER_DETAILS = "user-details";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "dashboard", "categorias-distintas", "upms-distintas", CACHE_USER_DETAILS);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(100));
        return manager;
    }
}

package com.fundacao.gerenciador_patrimonial.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Arquivos públicos para download em {@code /downloads/**} (ex.: o APK do app Android).
 *
 * <p>Procura primeiro em {@code <app.storage.pasta-raiz>/downloads/} (em produção, o volume
 * {@code /uploads}: trocar o APK não exige novo build) e depois em
 * {@code classpath:/public/downloads/} (arquivo empacotado no jar).</p>
 */
@Configuration
public class DownloadsConfig implements WebMvcConfigurer {

    public static final MediaType APK = MediaType.parseMediaType("application/vnd.android.package-archive");

    private final String pastaDownloads;

    public DownloadsConfig(@Value("${app.storage.pasta-raiz:./uploads}") String pastaRaiz) {
        this.pastaDownloads = Path.of(pastaRaiz, "downloads").toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/downloads/**")
                .addResourceLocations(pastaDownloads.endsWith("/") ? pastaDownloads : pastaDownloads + "/",
                        "classpath:/public/downloads/")
                // sempre revalida: ao publicar um APK novo, ninguém baixa a versão antiga do cache
                .setCacheControl(CacheControl.noCache());
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        // Tipo correto para o Android oferecer a instalação após o download.
        configurer.mediaType("apk", APK);
    }
}

package com.fundacao.gerenciador_patrimonial.service.storage;

import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.UUID;

/**
 * Implementação de {@link StorageService} que grava em uma pasta local.
 *
 * <p>Ativado pela propriedade {@code app.storage.tipo=LOCAL} (default).</p>
 */
@Service
@ConditionalOnProperty(name = "app.storage.tipo", havingValue = "LOCAL", matchIfMissing = true)
@Slf4j
public class LocalFileStorageService implements StorageService {

    private final Path pastaRaiz;

    public LocalFileStorageService(@Value("${app.storage.pasta-raiz:./uploads}") String pastaRaiz) {
        this.pastaRaiz = Paths.get(pastaRaiz).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(pastaRaiz);
            log.info("Storage local inicializado em: {}", pastaRaiz);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar a pasta de uploads: " + pastaRaiz, e);
        }
    }

    @Override
    public String armazenar(MultipartFile arquivo, String subpasta) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Arquivo vazio.");
        }

        String nomeOriginal = StringUtils.cleanPath(
                arquivo.getOriginalFilename() != null ? arquivo.getOriginalFilename() : "arquivo");
        // Gera um nome único para evitar colisão e exposição do nome original
        String extensao = extrairExtensao(nomeOriginal);
        String nomeFinal = UUID.randomUUID() + (extensao.isEmpty() ? "" : "." + extensao);

        Path destino = pastaRaiz.resolve(Paths.get(subpasta)).normalize().resolve(nomeFinal);

        // Proteção contra path traversal
        if (!destino.startsWith(pastaRaiz)) {
            throw new RegraDeNegocioException("Caminho de destino inválido.");
        }

        try {
            Files.createDirectories(destino.getParent());
            try (var in = arquivo.getInputStream()) {
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RegraDeNegocioException("Falha ao gravar arquivo: " + e.getMessage());
        }

        // Retorna o caminho relativo (é o que fica no banco)
        return pastaRaiz.relativize(destino).toString().replace('\\', '/');
    }

    @Override
    public Resource carregar(String caminhoArmazenamento) {
        try {
            Path arquivo = pastaRaiz.resolve(caminhoArmazenamento).normalize();
            if (!arquivo.startsWith(pastaRaiz)) {
                throw new RegraDeNegocioException("Caminho inválido.");
            }
            Resource resource = new UrlResource(arquivo.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new RegraDeNegocioException("Arquivo não disponível: " + caminhoArmazenamento);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new RegraDeNegocioException("URL inválida para o arquivo: " + e.getMessage());
        }
    }

    @Override
    public void deletar(String caminhoArmazenamento) {
        try {
            Path arquivo = pastaRaiz.resolve(caminhoArmazenamento).normalize();
            if (!arquivo.startsWith(pastaRaiz)) {
                throw new RegraDeNegocioException("Caminho inválido.");
            }
            Files.deleteIfExists(arquivo);
        } catch (IOException e) {
            log.warn("Falha ao deletar arquivo {}: {}", caminhoArmazenamento, e.getMessage());
        }
    }

    private String extrairExtensao(String nomeArquivo) {
        int idx = nomeArquivo.lastIndexOf('.');
        return (idx > 0 && idx < nomeArquivo.length() - 1)
                ? nomeArquivo.substring(idx + 1).toLowerCase()
                : "";
    }
}

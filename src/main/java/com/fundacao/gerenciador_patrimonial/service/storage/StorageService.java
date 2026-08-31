package com.fundacao.gerenciador_patrimonial.service.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Abstração de armazenamento de arquivos binários (anexos).
 *
 * <p>Implementações:
 * <ul>
 *   <li>{@link LocalFileStorageService} — salva em pasta local (Sprint 2/3)</li>
 *   <li>{@code S3StorageService} — futuro, Sprint 7</li>
 * </ul>
 * </p>
 */
public interface StorageService {

    /**
     * Persiste o arquivo no storage.
     *
     * @param arquivo  upload recebido
     * @param subpasta sub-diretório lógico (ex.: "patrimonio/2026/04")
     * @return identificador de armazenamento (caminho relativo ou URI) a ser salvo no DB
     */
    String armazenar(MultipartFile arquivo, String subpasta);

    /** Recupera o arquivo como recurso Spring para download. */
    Resource carregar(String caminhoArmazenamento);

    /** Remove o arquivo do storage (usado em exclusões permanentes). */
    void deletar(String caminhoArmazenamento);
}

package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.domain.enums.TipoAnexo;
import com.fundacao.gerenciador_patrimonial.dto.response.AnexoResponse;
import com.fundacao.gerenciador_patrimonial.service.AnexoService;
import com.fundacao.gerenciador_patrimonial.service.AnexoService.ArquivoParaDownload;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Endpoints de upload/download de anexos.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnexoController {

    private final AnexoService anexoService;

    @PostMapping(value = "/patrimonios/{patrimonioId}/anexos",
                 consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public AnexoResponse upload(@PathVariable Long patrimonioId,
                                @RequestParam("arquivo") MultipartFile arquivo,
                                @RequestParam(value = "tipo", required = false) TipoAnexo tipo) {
        return anexoService.anexar(patrimonioId, arquivo, tipo);
    }

    @GetMapping("/patrimonios/{patrimonioId}/anexos")
    public List<AnexoResponse> listar(@PathVariable Long patrimonioId) {
        return anexoService.listar(patrimonioId);
    }

    @GetMapping("/anexos/{anexoId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long anexoId) {
        ArquivoParaDownload arq = anexoService.baixar(anexoId);
        String contentType = arq.anexo().getContentType() != null
                ? arq.anexo().getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        // RFC 5987: encoding do nome para suportar acentos no Content-Disposition
        String nomeCodificado = java.net.URLEncoder.encode(
                arq.anexo().getNomeOriginal(), StandardCharsets.UTF_8);
        String contentDisposition = "attachment; filename*=UTF-8''" + nomeCodificado;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(arq.recurso());
    }

    @DeleteMapping("/anexos/{anexoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long anexoId) {
        anexoService.excluir(anexoId);
    }
}

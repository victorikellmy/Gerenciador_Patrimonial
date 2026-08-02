package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.ArquivoAnexo;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoAnexo;
import com.fundacao.gerenciador_patrimonial.dto.response.AnexoResponse;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.ArquivoAnexoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Upload / listagem / download / exclusão de anexos.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AnexoService {

    /** MIME types permitidos — defesa simples contra upload abusivo. */
    private static final Set<String> TIPOS_PERMITIDOS = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final String ENT = "Anexo";

    private final ArquivoAnexoRepository anexoRepo;
    private final PatrimonioRepository patrimonioRepo;
    private final StorageService storageService;
    private final AuditoriaService auditoriaService;

    public AnexoResponse anexar(Long patrimonioId, MultipartFile arquivo, TipoAnexo tipo) {
        Patrimonio patrimonio = patrimonioRepo.findById(patrimonioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Patrimônio", patrimonioId));

        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Arquivo vazio.");
        }
        if (arquivo.getContentType() == null || !TIPOS_PERMITIDOS.contains(arquivo.getContentType())) {
            throw new RegraDeNegocioException(
                    "Tipo de arquivo não permitido: " + arquivo.getContentType() +
                    " (aceitos: PDF, JPEG, PNG, WEBP).");
        }

        // Subpasta organizada por patrimônio/ano-mês para facilitar backup
        LocalDate hoje = LocalDate.now();
        String subpasta = "patrimonio/%d/%04d-%02d"
                .formatted(patrimonioId, hoje.getYear(), hoje.getMonthValue());

        String caminho = storageService.armazenar(arquivo, subpasta);

        ArquivoAnexo anexo = ArquivoAnexo.builder()
                .patrimonio(patrimonio)
                .nomeOriginal(arquivo.getOriginalFilename())
                .caminhoArmazenamento(caminho)
                .contentType(arquivo.getContentType())
                .tamanhoBytes(arquivo.getSize())
                .tipo(tipo != null ? tipo : TipoAnexo.OUTRO)
                .build();

        ArquivoAnexo salvo = anexoRepo.save(anexo);
        auditoriaService.registrar(AcaoAuditoria.ANEXAR, ENT, salvo.getId(),
                "Anexo no patrimônio #%d: %s (%s, %d bytes, tipo=%s)".formatted(
                        patrimonioId, salvo.getNomeOriginal(),
                        salvo.getContentType(), salvo.getTamanhoBytes(), salvo.getTipo()));
        return AnexoResponse.from(salvo);
    }

    @Transactional(readOnly = true)
    public List<AnexoResponse> listar(Long patrimonioId) {
        return anexoRepo.findByPatrimonioId(patrimonioId).stream()
                .map(AnexoResponse::from)
                .toList();
    }

    /**
     * @return par (entidade, recurso binário) para ser escrito no HTTP response
     */
    @Transactional(readOnly = true)
    public ArquivoParaDownload baixar(Long anexoId) {
        ArquivoAnexo anexo = anexoRepo.findById(anexoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo", anexoId));
        Resource resource = storageService.carregar(anexo.getCaminhoArmazenamento());
        return new ArquivoParaDownload(anexo, resource);
    }

    public void excluir(Long anexoId) {
        ArquivoAnexo anexo = anexoRepo.findById(anexoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo", anexoId));
        Long patrimonioId = anexo.getPatrimonio() != null ? anexo.getPatrimonio().getId() : null;
        String resumo = "Remoção de anexo do patrimônio #%s: %s".formatted(
                patrimonioId, anexo.getNomeOriginal());
        storageService.deletar(anexo.getCaminhoArmazenamento());
        anexoRepo.delete(anexo);
        auditoriaService.registrar(AcaoAuditoria.REMOVER_ANEXO, ENT, anexoId, resumo);
    }

    public record ArquivoParaDownload(ArquivoAnexo anexo, Resource recurso) {}
}

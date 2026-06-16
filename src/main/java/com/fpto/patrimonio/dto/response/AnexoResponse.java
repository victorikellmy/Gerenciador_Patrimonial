package com.fpto.patrimonio.dto.response;

import com.fpto.patrimonio.domain.entity.ArquivoAnexo;
import com.fpto.patrimonio.domain.enums.TipoAnexo;

import java.time.LocalDateTime;

public record AnexoResponse(
        Long id,
        String nomeOriginal,
        TipoAnexo tipo,
        String contentType,
        Long tamanhoBytes,
        LocalDateTime criadoEm
) {
    public static AnexoResponse from(ArquivoAnexo a) {
        return new AnexoResponse(
                a.getId(),
                a.getNomeOriginal(),
                a.getTipo(),
                a.getContentType(),
                a.getTamanhoBytes(),
                a.getCriadoEm()
        );
    }
}

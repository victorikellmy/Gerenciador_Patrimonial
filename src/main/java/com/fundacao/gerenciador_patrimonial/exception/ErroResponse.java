package com.fundacao.gerenciador_patrimonial.exception;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload padronizado de erro. Segue estilo RFC 7807 (problem details) simplificado.
 *
 * @param timestamp instante do erro
 * @param status    código HTTP
 * @param erro      nome curto do erro (ex.: "Recurso não encontrado")
 * @param mensagem  descrição amigável
 * @param path      URI que originou a falha
 * @param detalhes  lista de mensagens detalhadas (usada para erros de validação campo-a-campo)
 */
public record ErroResponse(
        LocalDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        String path,
        List<String> detalhes
) {
    public static ErroResponse of(int status, String erro, String mensagem, String path) {
        return new ErroResponse(LocalDateTime.now(), status, erro, mensagem, path, List.of());
    }

    public static ErroResponse of(int status, String erro, String mensagem, String path, List<String> detalhes) {
        return new ErroResponse(LocalDateTime.now(), status, erro, mensagem, path, detalhes);
    }
}

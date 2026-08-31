package com.fundacao.gerenciador_patrimonial.exception;

/**
 * Violação de regra de negócio (ex.: duplicidade, estado inválido).
 * Convertida em HTTP 409 pelo {@link GlobalExceptionHandler}.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}

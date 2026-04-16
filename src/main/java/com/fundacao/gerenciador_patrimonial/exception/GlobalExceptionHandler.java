package com.fundacao.gerenciador_patrimonial.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Conversor central de exceções em respostas HTTP uniformes (JSON).
 *
 * <p>Escopado ao pacote {@code controller} (REST) — os controllers web em
 * {@code com.fundacao.gerenciador_patrimonial.web} são tratados pelo
 * {@code WebExceptionHandler}.</p>
 */
@RestControllerAdvice(basePackages = "com.fundacao.gerenciador_patrimonial.controller")
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handleNotFound(RecursoNaoEncontradoException ex,
                                                      HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResponse.of(404, "Recurso não encontrado", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponse> handleBusiness(RegraDeNegocioException ex,
                                                      HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErroResponse.of(409, "Regra de negócio violada", ex.getMessage(), req.getRequestURI()));
    }

    /** Erros de validação do Bean Validation ({@code @Valid}). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidation(MethodArgumentNotValidException ex,
                                                        HttpServletRequest req) {
        List<String> detalhes = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(ErroResponse.of(400, "Erro de validação", "Campos inválidos", req.getRequestURI(), detalhes));
    }

    /** Constraints de unicidade do banco (ex.: matricula duplicada). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                            HttpServletRequest req) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErroResponse.of(409, "Conflito de dados",
                        "Violação de integridade: " + ex.getMostSpecificCause().getMessage(),
                        req.getRequestURI()));
    }

    /** Fallback — qualquer exceção não prevista. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Erro não tratado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErroResponse.of(500, "Erro interno", ex.getMessage(), req.getRequestURI()));
    }
}

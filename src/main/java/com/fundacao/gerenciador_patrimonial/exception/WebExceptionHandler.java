package com.fundacao.gerenciador_patrimonial.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.UUID;

/**
 * Tratamento de exceções para os controllers Web (views Thymeleaf).
 *
 * <p>Diferente do {@link GlobalExceptionHandler} (que retorna JSON para o pacote
 * REST), este emite uma página de erro amigável. Escopado ao pacote
 * {@code com.fundacao.gerenciador_patrimonial.web}.</p>
 */
@ControllerAdvice(basePackages = "com.fundacao.gerenciador_patrimonial.web")
@Slf4j
public class WebExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public String handleNotFound(RecursoNaoEncontradoException ex, Model model, HttpServletRequest req) {
        log.info("404 web: {} — {}", req.getRequestURI(), ex.getMessage());
        model.addAttribute("status", 404);
        model.addAttribute("titulo", "Recurso não encontrado");
        model.addAttribute("mensagem", ex.getMessage());
        return "erro";
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public String handleBusiness(RegraDeNegocioException ex, Model model, HttpServletRequest req) {
        log.info("409 web: {} — {}", req.getRequestURI(), ex.getMessage());
        model.addAttribute("status", 409);
        model.addAttribute("titulo", "Regra de negócio violada");
        model.addAttribute("mensagem", ex.getMessage());
        return "erro";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String handleDataIntegrity(DataIntegrityViolationException ex, Model model, HttpServletRequest req) {
        log.warn("Violação de integridade (web): {}", ex.getMostSpecificCause().getMessage());
        model.addAttribute("status", 409);
        model.addAttribute("titulo", "Conflito de dados");
        model.addAttribute("mensagem", "Violação de integridade: " + ex.getMostSpecificCause().getMessage());
        return "erro";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUploadTooBig(MaxUploadSizeExceededException ex, Model model) {
        model.addAttribute("status", 413);
        model.addAttribute("titulo", "Arquivo grande demais");
        model.addAttribute("mensagem", "O arquivo excede o tamanho máximo permitido.");
        return "erro";
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex, Model model, HttpServletRequest req) {
        String trackingId = UUID.randomUUID().toString();
        log.error("Erro não tratado em rota web [tracking={}]: {}", trackingId, req.getRequestURI(), ex);
        model.addAttribute("status", 500);
        model.addAttribute("titulo", "Erro interno");
        model.addAttribute("mensagem",
                "Falha inesperada. Informe ao suporte o código " + trackingId + ".");
        return "erro";
    }
}

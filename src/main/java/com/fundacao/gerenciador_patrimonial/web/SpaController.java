package com.fundacao.gerenciador_patrimonial.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entrega a SPA React (build em {@code classpath:/static/app/index.html}) para
 * qualquer rota de {@code /app} que não seja um arquivo estático, deixando o
 * roteamento com o React Router.
 */
@Controller
public class SpaController {

    @GetMapping({"/app", "/app/", "/app/{path:[^.]*}", "/app/{path:[^.]*}/{sub:[^.]*}", "/app/{path:[^.]*}/{sub:[^.]*}/{x:[^.]*}",
                 "/app/{path:[^.]*}/{sub:[^.]*}/{x:[^.]*}/{y:[^.]*}"})
    public String spa() {
        return "forward:/app/index.html";
    }
}

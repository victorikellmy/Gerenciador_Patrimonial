package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.dto.response.DashboardMetrics;
import com.fundacao.gerenciador_patrimonial.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Home da interface web — dashboard consolidado.
 * A inteligência agora vive em {@link DashboardService}; este controller
 * apenas injeta os dados no modelo.
 */
@Controller
@RequiredArgsConstructor
public class HomeWebController {

    private final DashboardService dashboardService;

    @GetMapping("/")
    public String home(Model model) {
        DashboardMetrics metrics = dashboardService.carregar();
        model.addAttribute("m", metrics);
        return "index";
    }
}

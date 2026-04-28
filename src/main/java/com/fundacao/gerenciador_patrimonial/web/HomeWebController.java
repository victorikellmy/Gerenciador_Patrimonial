package com.fundacao.gerenciador_patrimonial.web;

import com.fundacao.gerenciador_patrimonial.dto.response.DashboardMetrics;
import com.fundacao.gerenciador_patrimonial.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class HomeWebController {

    private final DashboardService dashboardService;

    @GetMapping("/")
    public String home(Model model) {
        long t0 = System.currentTimeMillis();
        DashboardMetrics metrics = dashboardService.carregar();
        long tService = System.currentTimeMillis() - t0;
        model.addAttribute("m", metrics);
        log.info("Dashboard: service concluído em {} ms — total={} ativos={} categorias={} upms={} movs={}",
                tService,
                metrics.totalPatrimonios(),
                metrics.contagemPorSituacao().getOrDefault("ATIVO", 0L),
                metrics.porCategoria().size(),
                metrics.topUpms().size(),
                metrics.ultimasMovimentacoes().size());
        return "index";
    }
}

package com.fundacao.gerenciador_patrimonial.controller;

import com.fundacao.gerenciador_patrimonial.domain.catalog.SubcategoriaCatalog;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Listas de apoio (dropdowns) para o app: UPMs, categorias e subcategorias. */
@RestController
@RequestMapping("/api/catalogos")
@RequiredArgsConstructor
public class CatalogoController {

    private final LotacaoRepository lotacaoRepository;
    private final PatrimonioRepository patrimonioRepository;
    private final SubcategoriaCatalog subcategoriaCatalog;

    @GetMapping("/upms")
    public List<String> upms() {
        return lotacaoRepository.findDistinctUpms();
    }

    @GetMapping("/categorias")
    public List<String> categorias() {
        return patrimonioRepository.findDistinctCategorias();
    }

    @GetMapping("/subcategorias")
    public List<String> subcategorias() {
        return subcategoriaCatalog.disponiveis();
    }
}

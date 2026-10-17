package com.fundacao.gerenciador_patrimonial;

import com.fundacao.gerenciador_patrimonial.domain.catalog.FrotaCatalog;
import com.fundacao.gerenciador_patrimonial.domain.entity.Deslocamento;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.entity.Veiculo;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.repository.AbastecimentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.DeslocamentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.ManutencaoVeiculoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import com.fundacao.gerenciador_patrimonial.repository.VeiculoRepository;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ponta a ponta da frota pela API usada pela SPA: cadastrar veículo (vira patrimônio
 * VEICULO) → saída em andamento → chegada → abastecimento → manutenção com próxima
 * revisão → ficha com histórico e km atual → exportação → NF obrigatória do MOTORISTA →
 * GERENTE somente leitura.
 */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha=",
        "app.frota.transportes.url=",
        "app.frota.seed=false"
})
@AutoConfigureMockMvc
class FrotaFluxoTest {

    private MockMvc mvc;
    private UsuarioAutenticado motorista;

    @Autowired WebApplicationContext ctx;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired LotacaoRepository lotacaoRepo;
    @Autowired ResponsavelRepository responsavelRepo;
    @Autowired PatrimonioRepository patrimonioRepo;
    @Autowired VeiculoRepository veiculoRepo;
    @Autowired AbastecimentoRepository abastecimentoRepo;
    @Autowired ManutencaoVeiculoRepository manutencaoRepo;
    @Autowired DeslocamentoRepository deslocamentoRepo;

    private Lotacao lotacao;
    private Responsavel responsavel;
    private String placa;

    @BeforeEach
    void setUp() {
        UsuarioAutenticado admin = new UsuarioAutenticado(usuario("admin.frota", Perfil.ADMINISTRADOR));
        motorista = new UsuarioAutenticado(usuario("motorista.teste", Perfil.MOTORISTA));
        mvc = MockMvcBuilders.webAppContextSetup(ctx)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user(admin)))
                .build();

        String sufixo = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        lotacao = lotacaoRepo.save(Lotacao.builder().upm("FROTA TESTE " + sufixo).nome("GARAGEM").tipoLocal(TipoLocal.INTERNO).build());
        responsavel = responsavelRepo.save(Responsavel.builder()
                .nomeCompleto("Responsável Frota " + sufixo).matricula("FR-" + sufixo).ativo(true).build());
        placa = "TST-" + sufixo.substring(0, 4);
    }

    private Usuario usuario(String login, Perfil perfil) {
        return usuarioRepo.findByLogin(login).orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Usuário " + login)
                .login(login)
                .senhaHash(encoder.encode("segredo-teste"))
                .perfil(perfil)
                .ativo(true)
                .build()));
    }

    private String veiculoJson(String placa, String modelo, String extras) {
        return """
                {"placa":"%s","modelo":"%s","lotacaoId":%d,"responsavelId":%d%s}"""
                .formatted(placa, modelo, lotacao.getId(), responsavel.getId(), extras);
    }

    private static String hoje() { return LocalDate.now().toString(); }

    @Test
    void fluxoCompletoDaFrota() throws Exception {
        // 1) Cadastrar veículo → cria patrimônio na categoria VEICULO
        mvc.perform(post("/api/frota/veiculos").contentType(MediaType.APPLICATION_JSON)
                        .content(veiculoJson(placa, "gol 1.6", """
                                ,"marca":"Volkswagen","ano":"2018","cidade":"Palmas","combustivelPadrao":"GASOLINA","kmAtual":50000,
                                "numeroTombo":"VEIC-%s","dataCompra":"2018-06-15","valorCompra":45000.00,"conservacao":"BOM\"""".formatted(placa))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value(placa));

        Veiculo v = veiculoRepo.findByPlacaIgnoreCase(placa).orElseThrow();
        Patrimonio p = patrimonioRepo.findById(v.getPatrimonio().getId()).orElseThrow();
        assertThat(p.getCategoria()).isEqualTo(FrotaCatalog.CATEGORIA_VEICULO);
        assertThat(p.getDescricao()).contains("GOL 1.6").contains(placa);
        assertThat(p.getNumeroTombo()).isEqualTo("VEIC-" + placa);
        assertThat(v.getKmAtual()).isEqualTo(50000);

        // Placa duplicada é recusada
        mvc.perform(post("/api/frota/veiculos").contentType(MediaType.APPLICATION_JSON)
                        .content(veiculoJson(placa.toLowerCase(), "outro", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("Já existe um veículo ativo com a placa")));

        // 2) Saída sem km final → veículo na rua
        mvc.perform(post("/api/frota/veiculos/" + v.getId() + "/deslocamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"veiculoId":%d,"dataViagem":"%s","destino":"HGP Palmas","motivo":"Assistência","titular":"Sgt. Silva",
                                 "horaSaida":"08:30","kmInicial":50010,"motorista":"João Motorista","lotacao":"FPTO - PALMAS"}"""
                                .formatted(v.getId(), hoje())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emAndamento").value(true));
        Deslocamento saida = deslocamentoRepo.findFirstByVeiculoIdAndKmFinalIsNullOrderByDataViagemDescIdDesc(v.getId()).orElseThrow();

        // Segunda saída enquanto na rua é recusada
        mvc.perform(post("/api/frota/veiculos/" + v.getId() + "/deslocamentos").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"veiculoId":%d,"dataViagem":"%s","destino":"Outro","motivo":"Apoio","motorista":"X"}""".formatted(v.getId(), hoje())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("já tem uma saída em andamento")));

        // Painel e ficha mostram "na rua"
        mvc.perform(get("/api/frota/resumo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.naRua", greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/frota/veiculos/" + v.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.veiculo.naRua").value(true))
                .andExpect(jsonPath("$.resumo.deslocamentoAberto.destino").value("HGP Palmas"));

        // 3) Chegada: km final menor que inicial é recusado; depois aceita
        mvc.perform(post("/api/frota/deslocamentos/" + saida.getId() + "/finalizar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaChegada\":\"11:00\",\"kmFinal\":50000}"))
                .andExpect(status().is4xxClientError());
        assertThat(deslocamentoRepo.findById(saida.getId()).orElseThrow().isEmAndamento()).isTrue();

        mvc.perform(post("/api/frota/deslocamentos/" + saida.getId() + "/finalizar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaChegada\":\"11:00\",\"kmFinal\":50090}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kmRodados").value(80));
        assertThat(veiculoRepo.findById(v.getId()).orElseThrow().getKmAtual()).isEqualTo(50090);

        // 4) Abastecimento e manutenção com próxima revisão (admin pode lançar sem NF)
        mvc.perform(post("/api/frota/veiculos/" + v.getId() + "/abastecimentos").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"veiculoId":%d,"data":"%s","km":50100,"combustivel":"GASOLINA","quantidadeLitros":40.5,"valor":250.00,
                                 "postoCidade":"Posto Central - Palmas","motorista":"João Motorista"}""".formatted(v.getId(), hoje())))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/frota/veiculos/" + v.getId() + "/manutencoes").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"veiculoId":%d,"data":"%s","servico":"REVISAO","km":50120,"descricao":"Revisão dos 50 mil","valor":890.00,
                                 "fornecedor":"Oficina Boa","proximaRevisaoKm":60000,"motorista":"João Motorista"}""".formatted(v.getId(), hoje())))
                .andExpect(status().isCreated());
        assertThat(abastecimentoRepo.countByVeiculoId(v.getId())).isEqualTo(1);
        assertThat(manutencaoRepo.countByVeiculoId(v.getId())).isEqualTo(1);
        assertThat(veiculoRepo.findById(v.getId()).orElseThrow().getKmAtual()).isEqualTo(50120);

        // Data futura é recusada
        mvc.perform(post("/api/frota/veiculos/" + v.getId() + "/abastecimentos").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"veiculoId":%d,"data":"%s","combustivel":"DIESEL","valor":10.00,"motorista":"X"}"""
                                .formatted(v.getId(), LocalDate.now().plusDays(2))))
                .andExpect(status().is4xxClientError());
        assertThat(abastecimentoRepo.countByVeiculoId(v.getId())).isEqualTo(1);

        // 5) Ficha: histórico unificado com os três lançamentos; resumo com próxima revisão
        mvc.perform(get("/api/frota/veiculos/" + v.getId() + "/historico")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.tipo == 'DESLOCAMENTO')].titulo").value(hasItem(containsString("HGP Palmas"))))
                .andExpect(jsonPath("$[?(@.tipo == 'ABASTECIMENTO')].detalhe").value(hasItem(containsString("Posto Central"))))
                .andExpect(jsonPath("$[?(@.tipo == 'MANUTENCAO')].detalhe").value(hasItem(containsString("Revisão dos 50 mil"))));
        mvc.perform(get("/api/frota/veiculos/" + v.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.proximaRevisaoKm").value(60000))
                .andExpect(jsonPath("$.resumo.kmAtual").value(50120));

        // Listagens gerais com filtro e exportações
        mvc.perform(get("/api/frota/abastecimentos").param("texto", placa.toLowerCase())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].postoCidade").value("Posto Central - Palmas"));
        mvc.perform(get("/api/frota/deslocamentos").param("somenteAbertos", "true")).andExpect(status().isOk());
        mvc.perform(get("/api/frota/manutencoes").param("veiculoId", v.getId().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fornecedor").value("Oficina Boa"));
        mvc.perform(get("/api/frota/export/historico").param("veiculoId", v.getId().toString()).param("formato", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".xlsx")));
        mvc.perform(get("/api/frota/export/abastecimentos").param("formato", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // 6) Patrimônio continua acessível pela API de patrimônios (é um bem como outro qualquer)
        mvc.perform(get("/api/patrimonios/" + p.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao", containsString(placa)));
    }

    @Test
    void motoristaPrecisaAnexarNotaFiscal() throws Exception {
        // veículo cadastrado pelo admin (default do MockMvc)
        mvc.perform(post("/api/frota/veiculos").contentType(MediaType.APPLICATION_JSON)
                        .content(veiculoJson(placa, "SAVEIRO", ",\"combustivelPadrao\":\"ETANOL\"")))
                .andExpect(status().isCreated());
        Veiculo v = veiculoRepo.findByPlacaIgnoreCase(placa).orElseThrow();
        String dados = """
                {"veiculoId":%d,"data":"%s","km":1000,"combustivel":"ETANOL","quantidadeLitros":30,"valor":120.00,"motorista":"Motorista Demo"}"""
                .formatted(v.getId(), hoje());

        // Motorista sem NF (JSON puro ou multipart sem arquivo) → recusado, nada gravado
        mvc.perform(post("/api/frota/veiculos/" + v.getId() + "/abastecimentos").with(user(motorista))
                        .contentType(MediaType.APPLICATION_JSON).content(dados))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("Anexe a nota fiscal")));
        mvc.perform(multipart("/api/frota/veiculos/" + v.getId() + "/abastecimentos")
                        .file(new MockMultipartFile("dados", "", "application/json", dados.getBytes())).with(user(motorista)))
                .andExpect(status().isConflict());
        assertThat(abastecimentoRepo.countByVeiculoId(v.getId())).isZero();

        // Com NF (foto) → grava e a NF fica disponível para download
        MockMultipartFile foto = new MockMultipartFile("arquivo", "cupom.jpg", "image/jpeg", "jpegdata".getBytes());
        mvc.perform(multipart("/api/frota/veiculos/" + v.getId() + "/abastecimentos")
                        .file(new MockMultipartFile("dados", "", "application/json", dados.getBytes())).file(foto).with(user(motorista)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possuiNotaFiscal").value(true))
                .andExpect(jsonPath("$.nfNomeOriginal").value("cupom.jpg"));
        var abast = abastecimentoRepo.findByVeiculoIdOrderByDataDescIdDesc(v.getId()).get(0);
        assertThat(abast.possuiNotaFiscal()).isTrue();
        mvc.perform(get("/api/frota/abastecimentos/" + abast.getId() + "/nota-fiscal").with(user(motorista)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        // Tipo de arquivo inválido é recusado
        String manut = """
                {"veiculoId":%d,"data":"%s","servico":"PNEUS","valor":900.00,"motorista":"Motorista Demo"}""".formatted(v.getId(), hoje());
        MockMultipartFile exe = new MockMultipartFile("arquivo", "x.exe", "application/octet-stream", "mz".getBytes());
        mvc.perform(multipart("/api/frota/veiculos/" + v.getId() + "/manutencoes")
                        .file(new MockMultipartFile("dados", "", "application/json", manut.getBytes())).file(exe).with(user(motorista)))
                .andExpect(status().is4xxClientError());

        // Admin pode lançar sem NF (registro antigo) e a resposta marca "sem NF"
        mvc.perform(post("/api/frota/veiculos/" + v.getId() + "/manutencoes").contentType(MediaType.APPLICATION_JSON).content(manut))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.possuiNotaFiscal").value(false));
    }

    @Test
    void gerenteSoLeEVePainelGerencial() throws Exception {
        UsuarioAutenticado gerente = new UsuarioAutenticado(usuario("gerente.teste", Perfil.GERENTE));

        // Admin cadastra um veículo e o envia para manutenção
        mvc.perform(post("/api/frota/veiculos").contentType(MediaType.APPLICATION_JSON)
                        .content(veiculoJson(placa, "AMB JUMPY", "")))
                .andExpect(status().isCreated());
        Veiculo v = veiculoRepo.findByPlacaIgnoreCase(placa).orElseThrow();
        mvc.perform(post("/api/frota/veiculos/" + v.getId() + "/manutencao-status")
                        .param("emManutencao", "true").param("motivo", "Troca de embreagem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("MANUTENCAO"));
        assertThat(patrimonioRepo.findById(v.getPatrimonio().getId()).orElseThrow().getSituacao().name()).isEqualTo("MANUTENCAO");

        // Gerente enxerga o painel gerencial com o veículo em manutenção, listas, dashboard, patrimônios e relatórios
        mvc.perform(get("/api/frota/gestao").with(user(gerente))).andExpect(status().isOk())
                .andExpect(jsonPath("$.emManutencao", greaterThanOrEqualTo(1)))
                .andExpect(content().string(containsString(placa)));
        mvc.perform(get("/api/frota/veiculos").with(user(gerente))).andExpect(status().isOk());
        mvc.perform(get("/api/frota/veiculos/" + v.getId()).with(user(gerente))).andExpect(status().isOk());
        mvc.perform(get("/api/frota/deslocamentos").with(user(gerente))).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard").with(user(gerente))).andExpect(status().isOk());
        mvc.perform(get("/api/patrimonios").with(user(gerente))).andExpect(status().isOk());
        mvc.perform(get("/api/catalogos/upms").with(user(gerente))).andExpect(status().isOk());
        mvc.perform(get("/api/relatorios/inventario").param("formato", "xlsx").with(user(gerente))).andExpect(status().isOk());
        mvc.perform(get("/api/frota/export/gestao").param("formato", "xlsx").with(user(gerente))).andExpect(status().isOk());

        // ...mas não altera nada
        mvc.perform(post("/api/frota/veiculos").with(user(gerente)).contentType(MediaType.APPLICATION_JSON)
                        .content(veiculoJson("GER-0001", "X", ""))).andExpect(status().isForbidden());
        mvc.perform(post("/api/patrimonios").with(user(gerente)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios").with(user(gerente))).andExpect(status().isForbidden());
        mvc.perform(post("/api/frota/veiculos/" + v.getId() + "/abastecimentos").with(user(gerente)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"veiculoId":%d,"data":"%s","combustivel":"DIESEL","valor":10.00,"motorista":"X"}""".formatted(v.getId(), hoje())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/frota/veiculos/" + v.getId() + "/manutencao-status").with(user(gerente)).param("emManutencao", "false"))
                .andExpect(status().isForbidden());
    }
}

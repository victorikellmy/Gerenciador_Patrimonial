package com.fundacao.gerenciador_patrimonial;

import com.fundacao.gerenciador_patrimonial.domain.entity.Diaria;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.entity.Titular;
import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.entity.Veiculo;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.repository.CautelaRepository;
import com.fundacao.gerenciador_patrimonial.repository.DiariaRepository;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.NivelDiariaRepository;
import com.fundacao.gerenciador_patrimonial.repository.ProfissionalRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.repository.TitularRepository;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import com.fundacao.gerenciador_patrimonial.repository.VeiculoRepository;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Diárias pela API (profissional → solicitação → confirmar → deferir → pagar, com perfis
 * PRESIDENTE e FINANCEIRO), PDF, relatório de pagas; cautela com termo em PDF e
 * encerramento; autocomplete de titulares.
 */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha=",
        "app.frota.transportes.url=",
        "app.frota.seed=false"
})
@AutoConfigureMockMvc
class DiariasECautelasFluxoTest {

    private MockMvc mvc;
    private UsuarioAutenticado presidente;
    private UsuarioAutenticado financeiro;
    private UsuarioAutenticado fiscal;

    @Autowired WebApplicationContext ctx;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired NivelDiariaRepository nivelRepo;
    @Autowired ProfissionalRepository profissionalRepo;
    @Autowired DiariaRepository diariaRepo;
    @Autowired CautelaRepository cautelaRepo;
    @Autowired TitularRepository titularRepo;
    @Autowired VeiculoRepository veiculoRepo;
    @Autowired LotacaoRepository lotacaoRepo;
    @Autowired ResponsavelRepository responsavelRepo;

    @BeforeEach
    void setUp() {
        UsuarioAutenticado admin = new UsuarioAutenticado(usuario("admin.diarias", Perfil.ADMINISTRADOR));
        presidente = new UsuarioAutenticado(usuario("presidente.teste", Perfil.PRESIDENTE));
        financeiro = new UsuarioAutenticado(usuario("financeiro.teste", Perfil.FINANCEIRO));
        fiscal = new UsuarioAutenticado(usuario("fiscal.diarias", Perfil.FISCAL));
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity())
                .defaultRequest(get("/").with(user(admin))).build();
    }

    private Usuario usuario(String login, Perfil perfil) {
        return usuarioRepo.findByLogin(login).orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Usuário " + login).login(login).senhaHash(encoder.encode("segredo-teste"))
                .perfil(perfil).ativo(true).build()));
    }

    @Test
    void fluxoCompletoDaDiaria() throws Exception {
        // Tabela de valores veio da migration V10
        assertThat(nivelRepo.findByAtivoTrueOrderByIdAsc()).hasSizeGreaterThanOrEqualTo(7);
        Long nivelId = nivelRepo.findByAtivoTrueOrderByIdAsc().get(4).getId(); // Assistente; Sgt

        // 1) Fiscal cadastra o profissional
        String cpf = "529.982.247-25";
        mvc.perform(post("/api/diarias/profissionais").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Sgt. Teste Diária","matricula":"M-%s","lotacao":"1º BPM - PALMAS","nivelId":%d,
                                 "cpf":"52998224725","dadosBancarios":"BB 1234 / 56789-0"}"""
                                .formatted(UUID.randomUUID().toString().substring(0, 5), nivelId)))
                .andExpect(status().isCreated());
        var prof = profissionalRepo.findFirstByCpf(cpf).orElseThrow();
        assertThat(prof.getNome()).isEqualTo("SGT. TESTE DIÁRIA");

        // Valores da tabela para o formulário
        mvc.perform(get("/api/diarias/valores/" + prof.getId()).with(user(fiscal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorCapitais").isNumber());

        // A busca do formulário acha o profissional pelo CPF sem pontuação
        mvc.perform(get("/api/diarias/profissionais/busca").param("termo", "52998224725").with(user(fiscal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(prof.getId()));

        // Pedido no formato antigo (app até a versão 1.4) é recusado com orientação
        mvc.perform(post("/api/diarias").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"profissionalId":%d,"itinerario":"Palmas → Gurupi → Palmas","partida":"%s","horaSaida":"06:00",
                                 "retorno":"%s","horaChegada":"20:00","meioTransporte":"VEICULO_OFICIAL","recurso":"PROPRIO",
                                 "numeroDiarias":2,"valorUnitario":243.23,"finalidade":"Instrução"}"""
                                .formatted(prof.getId(), LocalDate.now().plusDays(2), LocalDate.now().plusDays(3))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("aplicativo")));

        // 2) Fiscal abre o requerimento: 06:00 de D+2 às 20:00 de D+3 = 1 período de 24h + 0,5 de alimentação
        mvc.perform(post("/api/diarias").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content(requerimento(prof.getId(), "Sgt. Teste Diária", "52998224725", "1º BPM - PALMAS", "001",
                                LocalDate.now().plusDays(2), "06:00", LocalDate.now().plusDays(3), "20:00", "243.23")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SOLICITADO"))
                .andExpect(jsonPath("$.numeroDiarias").value(1.5))
                .andExpect(jsonPath("$.valorTotal").value(364.85))
                .andExpect(jsonPath("$.tipoDiaria").value("INTERIOR_ESTADO"))
                .andExpect(jsonPath("$.numeroRequerimento").value(org.hamcrest.Matchers.matchesPattern("\\d+/\\d{4}")))
                .andExpect(jsonPath("$.calculo.integrais").value(1));
        Diaria d = diariaRepo.findAll().stream().filter(x -> x.getProfissional().getId().equals(prof.getId())).findFirst().orElseThrow();
        // o cadastro passa a ter o que foi informado no requerimento
        var atualizado = profissionalRepo.findFirstByCpf(cpf).orElseThrow();
        assertThat(atualizado.getCargo()).isEqualTo("Segundo Sargento PM");
        assertThat(atualizado.getBanco()).isEqualTo("001");

        // Retorno antes da partida é recusado
        mvc.perform(post("/api/diarias").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content(requerimento(prof.getId(), "Sgt. Teste Diária", "52998224725", "1º BPM - PALMAS", "001",
                                LocalDate.now().plusDays(5), "06:00", LocalDate.now(), "20:00", "100")))
                .andExpect(status().is4xxClientError());

        // 3) Presidente não pode deferir ainda (precisa estar confirmada); fiscal não confirma; admin confirma
        mvc.perform(post("/api/diarias/" + d.getId() + "/deferir").with(user(presidente)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        assertThat(diariaRepo.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(StatusDiaria.SOLICITADO);
        mvc.perform(post("/api/diarias/" + d.getId() + "/confirmar").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/diarias/" + d.getId() + "/confirmar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));

        // Presidente vê as confirmadas por padrão e defere; financeiro não pode deferir
        mvc.perform(get("/api/diarias").param("padraoDoPerfil", "true").with(user(presidente))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)].itinerario".formatted(d.getId())).value("Gurupi"))
                .andExpect(jsonPath("$.content[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("CONFIRMADO"))));
        mvc.perform(post("/api/diarias/" + d.getId() + "/deferir").with(user(financeiro)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/diarias/" + d.getId() + "/indeferir").with(user(presidente)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError()); // sem motivo não indefere
        assertThat(diariaRepo.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(StatusDiaria.CONFIRMADO);
        mvc.perform(post("/api/diarias/" + d.getId() + "/deferir").with(user(presidente)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacao\":\"OK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEFERIDO"));

        // 4) Financeiro paga; data futura é recusada; diária paga não se exclui
        mvc.perform(post("/api/diarias/" + d.getId() + "/pagar").with(user(financeiro)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataPagamento\":\"%s\"}".formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().is4xxClientError());
        assertThat(diariaRepo.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(StatusDiaria.DEFERIDO);
        mvc.perform(post("/api/diarias/" + d.getId() + "/pagar").with(user(financeiro)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataPagamento\":\"%s\"}".formatted(LocalDate.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO"));
        Diaria paga = diariaRepo.findById(d.getId()).orElseThrow();
        assertThat(paga.getDataPagamento()).isEqualTo(LocalDate.now());
        mvc.perform(delete("/api/diarias/" + d.getId())).andExpect(status().is4xxClientError());
        assertThat(diariaRepo.findById(d.getId())).isPresent();

        // 5) Detalhe com histórico, PDF e relatório de pagas
        mvc.perform(get("/api/diarias/" + d.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO"))
                .andExpect(jsonPath("$.editavel").value(false));
        mvc.perform(get("/api/diarias/" + d.getId() + "/historico")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paraStatus").exists());
        mvc.perform(get("/api/diarias/" + d.getId() + "/pdf")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Requerimento_Diaria_")));
        mvc.perform(get("/api/diarias/" + d.getId() + "/extrato")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/diarias/pagas").with(user(financeiro))).andExpect(status().isOk())
                .andExpect(jsonPath("$.lista[?(@.id == %d)].profissionalNome".formatted(d.getId())).value("SGT. TESTE DIÁRIA"));
        mvc.perform(get("/api/diarias/pagas/download").param("formato", "xlsx")).andExpect(status().isOk());

        // Presidente/financeiro não abrem solicitação nem cadastram patrimônios
        mvc.perform(post("/api/diarias").with(user(presidente)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/patrimonios").with(user(financeiro)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/me/resumo").with(user(presidente))).andExpect(status().isOk());
    }

    /**
     * "Conforme vai solicitando, vai guardando": o primeiro requerimento cadastra o profissional;
     * no seguinte basta o CPF (ou a busca por nome) e o cadastro é reaproveitado e atualizado.
     */
    @Test
    void requerimentoCadastraOProfissionalEReaproveitaNaProximaVez() throws Exception {
        String cpfDigitos = "7" + String.format("%010d", (long) (Math.random() * 9_999_999_999L));
        String cpf = com.fundacao.gerenciador_patrimonial.service.DiariaService.formatarCpf(cpfDigitos);
        assertThat(profissionalRepo.findFirstByCpf(cpf)).isEmpty();
        LocalDate saida = LocalDate.now().plusDays(10);

        // 1ª vez: ninguém cadastrado; o requerimento cria o profissional
        String r1 = mvc.perform(post("/api/diarias").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content(requerimento(null, "Fulana de Tal Requerente", cpfDigitos, "QCG - PALMAS", "001",
                                saida, "08:00", saida.plusDays(2), "19:00", "318.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroDiarias").value(2.5))
                .andExpect(jsonPath("$.valorTotal").value(795.0))
                .andExpect(jsonPath("$.profissionalNome").value("FULANA DE TAL REQUERENTE"))
                .andReturn().getResponse().getContentAsString();
        var j1 = new com.fasterxml.jackson.databind.ObjectMapper().readTree(r1);
        long diaria1 = j1.get("id").asLong();
        var prof = profissionalRepo.findFirstByCpf(cpf).orElseThrow();
        assertThat(prof.getLotacao()).isEqualTo("QCG - PALMAS");
        assertThat(prof.getAgencia()).isEqualTo("1234-5");
        assertThat(prof.getPix()).isEqualTo("chave.pix@exemplo.org");

        // A busca por parte do nome traz todos os dados para preencher o formulário
        mvc.perform(get("/api/diarias/profissionais/busca").param("termo", "tal requer").with(user(fiscal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)].cargo".formatted(prof.getId())).value("Segundo Sargento PM"))
                .andExpect(jsonPath("$[?(@.id == %d)].contaCorrente".formatted(prof.getId())).value("56789-X"));
        mvc.perform(get("/api/diarias/profissionais/busca").param("termo", "tal").with(user(presidente)))
                .andExpect(status().isForbidden());

        // 2ª vez: só o CPF identifica; lotação e banco novos atualizam o cadastro, sem duplicar
        String r2 = mvc.perform(post("/api/diarias").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content(requerimento(null, "Fulana de Tal Requerente", cpfDigitos, "5º BPM - PORTO NACIONAL", "104",
                                saida.plusDays(20), "07:00", saida.plusDays(20), "13:00", "318.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroDiarias").value(0.5))
                .andExpect(jsonPath("$.profissionalId").value(prof.getId()))
                .andReturn().getResponse().getContentAsString();
        var j2 = new com.fasterxml.jackson.databind.ObjectMapper().readTree(r2);
        assertThat(profissionalRepo.findAll().stream().filter(p -> cpf.equals(p.getCpf()))).hasSize(1);
        assertThat(profissionalRepo.findFirstByCpf(cpf).orElseThrow().getLotacao()).isEqualTo("5º BPM - PORTO NACIONAL");
        // numeração em sequência dentro do ano
        String[] n1 = j1.get("numeroRequerimento").asText().split("/");
        String[] n2 = j2.get("numeroRequerimento").asText().split("/");
        if (n1[1].equals(n2[1])) assertThat(Integer.parseInt(n2[0])).isEqualTo(Integer.parseInt(n1[0]) + 1);
        // a primeira diária guarda os dados como eram no pedido dela
        mvc.perform(get("/api/diarias/" + diaria1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.profissionalLotacao").value("QCG - PALMAS"))
                .andExpect(jsonPath("$.banco").value("001"));

        // Regras do formulário: banco com 3 dígitos, nome sem números e afastamento que gera diária
        mvc.perform(post("/api/diarias").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content(requerimento(null, "Fulana de Tal Requerente", cpfDigitos, "QCG", "12",
                                saida, "08:00", saida.plusDays(1), "19:00", "318.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("Banco")));
        mvc.perform(post("/api/diarias").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content(requerimento(null, "Fulana 2 de Tal", cpfDigitos, "QCG", "001",
                                saida, "08:00", saida.plusDays(1), "19:00", "318.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("sem números")));
        mvc.perform(post("/api/diarias").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content(requerimento(null, "Fulana de Tal Requerente", cpfDigitos, "QCG", "001",
                                saida, "08:00", saida, "11:00", "318.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("não gera diária")));
        mvc.perform(post("/api/diarias").with(user(fiscal)).contentType(MediaType.APPLICATION_JSON)
                        .content(requerimento(null, "Fulana de Tal Requerente", cpfDigitos, "QCG", "001",
                                saida, "08:00", saida.plusDays(1), "19:00", "318.00").replace("\"tipoDiaria\":\"INTERIOR_ESTADO\",", "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("tipo de diária")));

        // Prévia do cálculo usada pelo formulário
        mvc.perform(get("/api/diarias/calculo").param("partida", saida.toString()).param("horaSaida", "08:00")
                        .param("retorno", saida.plusDays(2).toString()).param("horaChegada", "19:00").with(user(fiscal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(2.5))
                .andExpect(jsonPath("$.refeicoes.length()").value(2));
    }

    /** Requerimento completo no contrato novo (Anexo II). */
    private static String requerimento(Long profissionalId, String nome, String cpf, String lotacao, String banco,
                                       LocalDate partida, String horaSaida, LocalDate retorno, String horaChegada, String valorUnitario) {
        return """
                {"profissionalId":%s,"nome":"%s","matricula":"12345-6","cargo":"Segundo Sargento PM","lotacao":"%s",
                 "cpf":"%s","rg":"1234567","orgaoExpedidor":"SSP/TO",
                 "origem":"Palmas","itinerario":"Gurupi","partida":"%s","horaSaida":"%s","retorno":"%s","horaChegada":"%s",
                 "meioTransporte":"VEICULO_OFICIAL","recurso":"PROPRIO","finalidade":"Instrução no 4º BPM.",
                 "banco":"%s","agencia":"1234-5","contaCorrente":"56789-x","pix":"chave.pix@exemplo.org",
                 "tipoDiaria":"INTERIOR_ESTADO","valorUnitario":%s}"""
                .formatted(profissionalId, nome, lotacao, cpf, partida, horaSaida, retorno, horaChegada, banco, valorUnitario);
    }

    @Test
    void cautelaComTermoEAutocompleteDeTitulares() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        Lotacao lot = lotacaoRepo.save(Lotacao.builder().upm("FROTA CAUTELA " + sufixo).nome("GARAGEM").tipoLocal(TipoLocal.INTERNO).build());
        Responsavel resp = responsavelRepo.save(Responsavel.builder().nomeCompleto("Resp Cautela " + sufixo).matricula("RC-" + sufixo).ativo(true).build());
        mvc.perform(post("/api/frota/veiculos").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placa":"CAU-%s","modelo":"SAVEIRO","lotacaoId":%d,"responsavelId":%d}""".formatted(sufixo, lot.getId(), resp.getId())))
                .andExpect(status().isCreated());
        Veiculo v = veiculoRepo.findByPlacaIgnoreCase("CAU-" + sufixo).orElseThrow();

        titularRepo.save(Titular.builder().nome("CARLOS AUGUSTO PEREIRA").tipoCadastro("M").build());
        mvc.perform(get("/api/frota/titulares").param("termo", "carlos aug")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("CARLOS AUGUSTO PEREIRA"));

        // CPF inválido é recusado
        mvc.perform(post("/api/frota/cautelas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"veiculoId":%d,"dataViagem":"%s","destino":"9º BPM","motivo":"Cautela","kmInicial":100,
                                 "motoristaNome":"Cb. Carlos","rg":"123","cpf":"123","fone":"63 9999","lotacao":"9º BPM"}"""
                                .formatted(v.getId(), LocalDate.now())))
                .andExpect(status().is4xxClientError());

        // Cautela aberta, termo em PDF, segunda cautela recusada, encerramento
        mvc.perform(post("/api/frota/cautelas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"veiculoId":%d,"dataViagem":"%s","destino":"9º BPM","motivo":"Cautela","horaSaida":"07:00","kmInicial":100,
                                 "motoristaNome":"Cb. Carlos Augusto","rg":"1234567","cpf":"52998224725","fone":"(63) 99999-0000",
                                 "lotacao":"9º BPM - ARAGUATINS"}""".formatted(v.getId(), LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpf").value("529.982.247-25"));
        var cautela = cautelaRepo.findFirstByVeiculoIdAndKmFinalIsNullOrderByDataViagemDescIdDesc(v.getId()).orElseThrow();
        mvc.perform(get("/api/frota/cautelas/" + cautela.getId() + "/termo")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/frota/cautelas").param("veiculoId", v.getId().toString())).andExpect(status().isOk())
                .andExpect(content().string(containsString("CB. CARLOS AUGUSTO")));
        mvc.perform(post("/api/frota/cautelas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"veiculoId":%d,"dataViagem":"%s","destino":"Outro","motivo":"Cautela",
                                 "motoristaNome":"X","rg":"1","cpf":"52998224725","fone":"1","lotacao":"Y"}""".formatted(v.getId(), LocalDate.now())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem", containsString("cautela em aberto")));
        mvc.perform(post("/api/frota/cautelas/" + cautela.getId() + "/finalizar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaChegada\":\"18:00\",\"kmFinal\":250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kmRodados").value(150));
        assertThat(veiculoRepo.findById(v.getId()).orElseThrow().getKmAtual()).isEqualTo(250);
    }
}

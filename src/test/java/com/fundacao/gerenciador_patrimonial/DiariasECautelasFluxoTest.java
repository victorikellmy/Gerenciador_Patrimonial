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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Diárias (profissional → solicitação → confirmar → deferir → pagar, com perfis
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
        mvc.perform(post("/diarias/profissionais").with(csrf()).with(user(fiscal))
                        .param("nome", "Sgt. Teste Diária").param("matricula", "M-" + UUID.randomUUID().toString().substring(0, 5))
                        .param("lotacao", "1º BPM - PALMAS").param("nivelId", nivelId.toString()).param("cpf", "52998224725")
                        .param("dadosBancarios", "BB 1234 / 56789-0"))
                .andExpect(status().is3xxRedirection());
        var prof = profissionalRepo.findFirstByCpf(cpf).orElseThrow();
        assertThat(prof.getNome()).isEqualTo("SGT. TESTE DIÁRIA");

        // Valores da tabela para o formulário
        mvc.perform(get("/diarias/valores/" + prof.getId()).with(user(fiscal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capitais").isNumber());

        // 2) Fiscal abre a solicitação (2 diárias × valor interior do Estado)
        mvc.perform(post("/diarias").with(csrf()).with(user(fiscal))
                        .param("profissionalId", prof.getId().toString()).param("itinerario", "Palmas → Gurupi → Palmas")
                        .param("partida", LocalDate.now().plusDays(2).toString()).param("horaSaida", "06:00")
                        .param("retorno", LocalDate.now().plusDays(3).toString()).param("horaChegada", "20:00")
                        .param("meioTransporte", "VEICULO_OFICIAL").param("recurso", "PROPRIO")
                        .param("numeroDiarias", "2").param("valorUnitario", "243.23").param("finalidade", "Instrução"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/diarias/*"));
        Diaria d = diariaRepo.findAll().stream().filter(x -> x.getProfissional().getId().equals(prof.getId())).findFirst().orElseThrow();
        assertThat(d.getStatus()).isEqualTo(StatusDiaria.SOLICITADO);
        assertThat(d.getValorTotal()).isEqualByComparingTo("486.46");

        // Retorno antes da partida é recusado
        mvc.perform(post("/diarias").with(csrf()).with(user(fiscal))
                        .param("profissionalId", prof.getId().toString()).param("itinerario", "X")
                        .param("partida", LocalDate.now().plusDays(5).toString()).param("retorno", LocalDate.now().toString())
                        .param("meioTransporte", "AEREO").param("recurso", "PROPRIO")
                        .param("numeroDiarias", "1").param("valorUnitario", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("não pode ser antes da partida")));

        // 3) Presidente não pode deferir ainda (precisa estar confirmada); fiscal não confirma; admin confirma
        mvc.perform(post("/diarias/" + d.getId() + "/deferir").with(csrf()).with(user(presidente))).andExpect(status().is3xxRedirection());
        assertThat(diariaRepo.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(StatusDiaria.SOLICITADO);
        mvc.perform(post("/diarias/" + d.getId() + "/confirmar").with(csrf()).with(user(fiscal))).andExpect(status().isForbidden());
        mvc.perform(post("/diarias/" + d.getId() + "/confirmar").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(diariaRepo.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(StatusDiaria.CONFIRMADO);

        // Presidente vê as confirmadas por padrão e defere; financeiro não pode deferir
        mvc.perform(get("/diarias").with(user(presidente))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Palmas → Gurupi → Palmas")));
        mvc.perform(post("/diarias/" + d.getId() + "/deferir").with(csrf()).with(user(financeiro))).andExpect(status().isForbidden());
        mvc.perform(post("/diarias/" + d.getId() + "/indeferir").with(csrf()).with(user(presidente))).andExpect(status().is3xxRedirection());
        assertThat(diariaRepo.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(StatusDiaria.CONFIRMADO); // sem motivo não indefere
        mvc.perform(post("/diarias/" + d.getId() + "/deferir").with(csrf()).with(user(presidente)).param("observacao", "OK"))
                .andExpect(status().is3xxRedirection());
        assertThat(diariaRepo.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(StatusDiaria.DEFERIDO);

        // 4) Financeiro paga; data futura é recusada; diária paga não se edita
        mvc.perform(post("/diarias/" + d.getId() + "/pagar").with(csrf()).with(user(financeiro))
                        .param("dataPagamento", LocalDate.now().plusDays(1).toString())).andExpect(status().is3xxRedirection());
        assertThat(diariaRepo.findById(d.getId()).orElseThrow().getStatus()).isEqualTo(StatusDiaria.DEFERIDO);
        mvc.perform(post("/diarias/" + d.getId() + "/pagar").with(csrf()).with(user(financeiro))
                        .param("dataPagamento", LocalDate.now().toString())).andExpect(status().is3xxRedirection());
        Diaria paga = diariaRepo.findById(d.getId()).orElseThrow();
        assertThat(paga.getStatus()).isEqualTo(StatusDiaria.PAGO);
        assertThat(paga.getDataPagamento()).isEqualTo(LocalDate.now());
        mvc.perform(post("/diarias/" + d.getId() + "/excluir").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(diariaRepo.findById(d.getId())).isPresent();

        // 5) Detalhe com histórico, PDF e relatório de pagas
        mvc.perform(get("/diarias/" + d.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Paga")))
                .andExpect(content().string(containsString("Solicitação aberta.")));
        mvc.perform(get("/diarias/" + d.getId() + "/pdf")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/diarias/pagas").with(user(financeiro))).andExpect(status().isOk())
                .andExpect(content().string(containsString("SGT. TESTE DIÁRIA")));
        mvc.perform(get("/diarias/pagas/download").param("formato", "xlsx")).andExpect(status().isOk());

        // Presidente/financeiro não abrem solicitação nem acessam patrimônios
        mvc.perform(get("/diarias/nova").with(user(presidente))).andExpect(status().isForbidden());
        mvc.perform(get("/patrimonios/novo").with(user(financeiro))).andExpect(status().isForbidden());
        mvc.perform(get("/").with(user(presidente))).andExpect(status().isOk());
    }

    @Test
    void cautelaComTermoEAutocompleteDeTitulares() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        Lotacao lot = lotacaoRepo.save(Lotacao.builder().upm("FROTA CAUTELA " + sufixo).nome("GARAGEM").tipoLocal(TipoLocal.INTERNO).build());
        Responsavel resp = responsavelRepo.save(Responsavel.builder().nomeCompleto("Resp Cautela " + sufixo).matricula("RC-" + sufixo).ativo(true).build());
        mvc.perform(post("/frota").with(csrf()).param("placa", "CAU-" + sufixo).param("modelo", "SAVEIRO")
                        .param("lotacaoId", lot.getId().toString()).param("responsavelId", resp.getId().toString()))
                .andExpect(status().is3xxRedirection());
        Veiculo v = veiculoRepo.findByPlacaIgnoreCase("CAU-" + sufixo).orElseThrow();

        titularRepo.save(Titular.builder().nome("CARLOS AUGUSTO PEREIRA").tipoCadastro("M").build());
        mvc.perform(get("/frota/titulares").param("termo", "carlos aug")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("CARLOS AUGUSTO PEREIRA"));

        // CPF inválido é recusado
        mvc.perform(post("/frota/cautelas").with(csrf())
                        .param("veiculoId", v.getId().toString()).param("dataViagem", LocalDate.now().toString())
                        .param("destino", "9º BPM").param("motivo", "Cautela").param("kmInicial", "100")
                        .param("motoristaNome", "Cb. Carlos").param("rg", "123").param("cpf", "123").param("fone", "63 9999").param("lotacao", "9º BPM"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CPF do condutor")));

        // Cautela aberta, termo em PDF, segunda cautela recusada, encerramento
        mvc.perform(post("/frota/cautelas").with(csrf())
                        .param("veiculoId", v.getId().toString()).param("dataViagem", LocalDate.now().toString())
                        .param("destino", "9º BPM").param("motivo", "Cautela").param("horaSaida", "07:00").param("kmInicial", "100")
                        .param("motoristaNome", "Cb. Carlos Augusto").param("rg", "1234567").param("cpf", "52998224725")
                        .param("fone", "(63) 99999-0000").param("lotacao", "9º BPM - ARAGUATINS"))
                .andExpect(status().is3xxRedirection());
        var cautela = cautelaRepo.findFirstByVeiculoIdAndKmFinalIsNullOrderByDataViagemDescIdDesc(v.getId()).orElseThrow();
        assertThat(cautela.getCpf()).isEqualTo("529.982.247-25");
        mvc.perform(get("/frota/cautelas/" + cautela.getId() + "/termo")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/frota/cautelas")).andExpect(status().isOk())
                .andExpect(content().string(containsString("CB. CARLOS AUGUSTO")));
        mvc.perform(post("/frota/cautelas").with(csrf())
                        .param("veiculoId", v.getId().toString()).param("dataViagem", LocalDate.now().toString())
                        .param("destino", "Outro").param("motivo", "Cautela")
                        .param("motoristaNome", "X").param("rg", "1").param("cpf", "52998224725").param("fone", "1").param("lotacao", "Y"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cautela em aberto")));
        mvc.perform(post("/frota/cautelas/" + cautela.getId() + "/finalizar").with(csrf())
                        .param("horaChegada", "18:00").param("kmFinal", "250")).andExpect(status().is3xxRedirection());
        assertThat(cautelaRepo.findById(cautela.getId()).orElseThrow().getKmRodados()).isEqualTo(150);
        assertThat(veiculoRepo.findById(v.getId()).orElseThrow().getKmAtual()).isEqualTo(250);
    }
}

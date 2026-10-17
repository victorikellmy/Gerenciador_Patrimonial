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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ponta a ponta da frota: cadastrar veículo (vira patrimônio VEICULO) → saída em
 * andamento → chegada → abastecimento → manutenção com próxima revisão → ficha com
 * histórico e km atual → exportação → regras de acesso do perfil MOTORISTA → API.
 */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha=",
        "app.frota.transportes.url="
})
@AutoConfigureMockMvc
class FrotaFluxoTest {

    private MockMvc mvc;
    private UsuarioAutenticado motorista;
    private String senhaMotorista;

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
        senhaMotorista = "segredo-teste";
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

    @Test
    void fluxoCompletoDaFrota() throws Exception {
        // 1) Cadastrar veículo → cria patrimônio na categoria VEICULO
        mvc.perform(post("/frota").with(csrf())
                        .param("placa", placa).param("modelo", "gol 1.6").param("marca", "Volkswagen")
                        .param("ano", "2018").param("cidade", "Palmas").param("combustivelPadrao", "GASOLINA")
                        .param("kmAtual", "50000")
                        .param("numeroTombo", "VEIC-" + placa).param("dataCompra", "2018-06-15")
                        .param("valorCompra", "45000.00").param("conservacao", "BOM")
                        .param("lotacaoId", lotacao.getId().toString())
                        .param("responsavelId", responsavel.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/frota/*"));

        Veiculo v = veiculoRepo.findByPlacaIgnoreCase(placa).orElseThrow();
        Patrimonio p = patrimonioRepo.findById(v.getPatrimonio().getId()).orElseThrow();
        assertThat(p.getCategoria()).isEqualTo(FrotaCatalog.CATEGORIA_VEICULO);
        assertThat(p.getDescricao()).contains("GOL 1.6").contains(placa);
        assertThat(p.getNumeroTombo()).isEqualTo("VEIC-" + placa);
        assertThat(v.getKmAtual()).isEqualTo(50000);

        // Placa duplicada é recusada
        mvc.perform(post("/frota").with(csrf())
                        .param("placa", placa.toLowerCase()).param("modelo", "outro")
                        .param("lotacaoId", lotacao.getId().toString())
                        .param("responsavelId", responsavel.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Já existe um veículo ativo com a placa")));

        // 2) Saída sem km final → veículo na rua
        mvc.perform(post("/frota/deslocamentos").with(csrf())
                        .param("veiculoId", v.getId().toString()).param("dataViagem", LocalDate.now().toString())
                        .param("destino", "HGP Palmas").param("motivo", "Assistência").param("titular", "Sgt. Silva")
                        .param("horaSaida", "08:30").param("kmInicial", "50010").param("motorista", "João Motorista")
                        .param("lotacao", "FPTO - PALMAS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/frota/" + v.getId()));
        Deslocamento saida = deslocamentoRepo.findFirstByVeiculoIdAndKmFinalIsNullOrderByDataViagemDescIdDesc(v.getId()).orElseThrow();
        assertThat(saida.isEmAndamento()).isTrue();

        // Segunda saída enquanto na rua é recusada
        mvc.perform(post("/frota/deslocamentos").with(csrf())
                        .param("veiculoId", v.getId().toString()).param("dataViagem", LocalDate.now().toString())
                        .param("destino", "Outro").param("motivo", "Apoio").param("motorista", "X"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("já tem uma saída em andamento")));

        // Painel e ficha mostram "na rua"
        mvc.perform(get("/frota")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Veículos na rua")))
                .andExpect(content().string(containsString(placa)));
        mvc.perform(get("/frota/" + v.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Registrar chegada")));

        // 3) Chegada: km final menor que inicial é recusado; depois aceita
        mvc.perform(post("/frota/deslocamentos/" + saida.getId() + "/finalizar").with(csrf())
                        .param("horaChegada", "11:00").param("kmFinal", "50000"))
                .andExpect(status().is3xxRedirection());
        assertThat(deslocamentoRepo.findById(saida.getId()).orElseThrow().isEmAndamento()).isTrue();

        mvc.perform(post("/frota/deslocamentos/" + saida.getId() + "/finalizar").with(csrf())
                        .param("horaChegada", "11:00").param("kmFinal", "50090"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/frota/" + v.getId()));
        Deslocamento fechado = deslocamentoRepo.findById(saida.getId()).orElseThrow();
        assertThat(fechado.getKmRodados()).isEqualTo(80);
        assertThat(veiculoRepo.findById(v.getId()).orElseThrow().getKmAtual()).isEqualTo(50090);

        // 4) Abastecimento e manutenção com próxima revisão
        mvc.perform(post("/frota/abastecimentos").with(csrf())
                        .param("veiculoId", v.getId().toString()).param("data", LocalDate.now().toString())
                        .param("km", "50100").param("combustivel", "GASOLINA").param("quantidadeLitros", "40.5")
                        .param("valor", "250.00").param("postoCidade", "Posto Central - Palmas").param("motorista", "João Motorista"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/frota/manutencoes").with(csrf())
                        .param("veiculoId", v.getId().toString()).param("data", LocalDate.now().toString())
                        .param("servico", "REVISAO").param("km", "50120").param("descricao", "Revisão dos 50 mil")
                        .param("valor", "890.00").param("fornecedor", "Oficina Boa").param("proximaRevisaoKm", "60000")
                        .param("motorista", "João Motorista"))
                .andExpect(status().is3xxRedirection());
        assertThat(abastecimentoRepo.countByVeiculoId(v.getId())).isEqualTo(1);
        assertThat(manutencaoRepo.countByVeiculoId(v.getId())).isEqualTo(1);
        assertThat(veiculoRepo.findById(v.getId()).orElseThrow().getKmAtual()).isEqualTo(50120);

        // Data futura é recusada
        mvc.perform(post("/frota/abastecimentos").with(csrf())
                        .param("veiculoId", v.getId().toString()).param("data", LocalDate.now().plusDays(2).toString())
                        .param("combustivel", "DIESEL").param("valor", "10.00").param("motorista", "X"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("não pode estar no futuro")));

        // 5) Ficha: histórico unificado com os três lançamentos e filtros
        mvc.perform(get("/frota/" + v.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("HGP Palmas")))
                .andExpect(content().string(containsString("Posto Central")))
                .andExpect(content().string(containsString("Revisão dos 50 mil")))
                .andExpect(content().string(containsString("60.000 km")));
        mvc.perform(get("/frota/" + v.getId()).param("tipo", "ABASTECIMENTO")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Posto Central")));

        // Listagens gerais com filtro e exportações
        mvc.perform(get("/frota/abastecimentos").param("texto", placa.toLowerCase())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Posto Central")));
        mvc.perform(get("/frota/deslocamentos").param("somenteAbertos", "true")).andExpect(status().isOk());
        mvc.perform(get("/frota/manutencoes").param("veiculoId", v.getId().toString())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Oficina Boa")));
        mvc.perform(get("/frota/" + v.getId() + "/historico/download").param("formato", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".xlsx")));
        mvc.perform(get("/frota/abastecimentos/download").param("formato", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // 6) Patrimônio continua acessível pela tela de patrimônios (é um bem como outro qualquer)
        mvc.perform(get("/patrimonios/" + p.getId() + "/editar")).andExpect(status().isOk())
                .andExpect(content().string(containsString(placa)));
    }

    @Test
    void motoristaSoAcessaFrota() throws Exception {
        mvc.perform(get("/frota").with(user(motorista))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Frota")));
        mvc.perform(get("/frota/abastecimentos/novo").with(user(motorista))).andExpect(status().isOk());
        mvc.perform(get("/patrimonios").with(user(motorista))).andExpect(status().isForbidden());
        mvc.perform(get("/").with(user(motorista))).andExpect(status().isForbidden());
        mvc.perform(get("/frota/novo").with(user(motorista))).andExpect(status().isForbidden());
        mvc.perform(post("/frota/abastecimentos/1/excluir").with(csrf()).with(user(motorista))).andExpect(status().isForbidden());

        // Motorista registra pela API (app Android) e é bloqueado fora da frota
        mvc.perform(get("/api/frota/veiculos").with(httpBasic("motorista.teste", senhaMotorista)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/frota/catalogos").with(httpBasic("motorista.teste", senhaMotorista)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivos").isArray());
        mvc.perform(get("/api/patrimonios").with(httpBasic("motorista.teste", senhaMotorista)))
                .andExpect(status().isForbidden());
    }
}

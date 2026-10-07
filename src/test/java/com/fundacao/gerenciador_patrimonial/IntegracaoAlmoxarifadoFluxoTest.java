package com.fundacao.gerenciador_patrimonial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundacao.gerenciador_patrimonial.domain.entity.ArquivoAnexo;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusPendencia;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoAnexo;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.repository.ArquivoAnexoRepository;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.PendenciaPatrimoniamentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ponta a ponta da integração Almoxarifado → Patrimônio:
 * POST do recebimento (Basic, perfil INTEGRACAO) → pendência → tela de patrimoniar
 * pré-preenchida → POST do formulário → patrimônio criado com a NF anexada → pendência concluída.
 */
@SpringBootTest(properties = {
        "app.importacao.habilitada=false",
        "app.storage.pasta-raiz=build/test-uploads",
        "app.integracao.almoxarifado.senha="
})
@AutoConfigureMockMvc
class IntegracaoAlmoxarifadoFluxoTest {

    private static final String ENDPOINT = "/api/integracao/almoxarifado/recebimentos";
    private static final String LOGIN_INTEGRACAO = "integracao.teste";
    private static final String SENHA_INTEGRACAO = "segredo-teste";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder encoder;
    @Autowired PendenciaPatrimoniamentoRepository pendenciaRepo;
    @Autowired PatrimonioRepository patrimonioRepo;
    @Autowired ArquivoAnexoRepository anexoRepo;
    @Autowired LotacaoRepository lotacaoRepo;
    @Autowired ResponsavelRepository responsavelRepo;

    private Long lotacaoId;
    private Long responsavelId;

    @BeforeEach
    void setUp() {
        if (!usuarioRepo.existsByLogin(LOGIN_INTEGRACAO)) {
            usuarioRepo.save(Usuario.builder()
                    .nomeCompleto("Integração de teste")
                    .login(LOGIN_INTEGRACAO)
                    .senhaHash(encoder.encode(SENHA_INTEGRACAO))
                    .perfil(Perfil.INTEGRACAO)
                    .ativo(true)
                    .build());
        }
        if (lotacaoId == null) {
            // Idempotente: o contexto (e o H2) é compartilhado entre os métodos da classe,
            // e (upm, nome) é único — reaproveita a lotação se já existir.
            Lotacao l = lotacaoRepo.findByUpmAndNome("5 BPM", "NÚCLEO DE SAÚDE")
                    .orElseGet(() -> lotacaoRepo.save(Lotacao.builder()
                            .upm("5 BPM").nome("NÚCLEO DE SAÚDE").cidade("Porto Nacional")
                            .tipoLocal(TipoLocal.values()[0]).build()));
            Responsavel r = responsavelRepo.save(Responsavel.builder()
                    .nomeCompleto("Sgt. João Silva").matricula("MAT-" + System.nanoTime()).lotacao(l).build());
            lotacaoId = l.getId();
            responsavelId = r.getId();
        }
    }

    /**
     * Operador FISCAL como principal real ({@link UsuarioAutenticado}): o layout
     * das telas lê {@code principal.usuario.perfil}, o que @WithMockUser não oferece.
     */
    private RequestPostProcessor fiscal() {
        Usuario u = usuarioRepo.findByLogin("fiscal.teste").orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Fiscal de teste")
                .login("fiscal.teste")
                .senhaHash(encoder.encode("segredo-teste"))
                .perfil(Perfil.FISCAL)
                .ativo(true)
                .build()));
        return user(new UsuarioAutenticado(u));
    }

    private String payload(long compraId, int quantidade, boolean comNf) {
        String nfBase64 = Base64.getEncoder().encodeToString(
                "%PDF-1.4 conteudo de teste da nota fiscal".getBytes(StandardCharsets.UTF_8));
        return """
            {
              "origem": "ALMOXARIFADO",
              "compraId": %d,
              "numeroDocumento": "001/2026",
              "numeroSgd": "2026/09039/000362",
              "assunto": "Solicitação para pagamento",
              "solicitanteDocumento": "Emerson Rodrigues Moura - MAJ QOPM",
              "fornecedor": "DAVID WELLYNGTON VAZ-ME",
              "numeroNotaFiscal": "00000002",
              "dataRecebimento": "2026-10-03T14:22:10",
              "dataCompra": "2026-10-03",
              "valorTotal": 640.00,
              "retiradoPor": "Sgt. João Silva",
              "setorDestino": "5º BPM - Núcleo de Saúde",
              "setorDestinoCentroCusto": "CC-05",
              "registradoPor": "sarah.luz",
              "observacao": "Reparo impressora Samsung · Parte/Ofício 001/2026",
              "itens": [
                { "descricao": "Impressora multifuncional Samsung", "codigoSku": null, "quantidade": %d, "valorUnitario": 320.00 }
              ],
              "anexos": [
                { "tipo": "SOLICITACAO", "nomeOriginal": "Oficio 001-2026.pdf", "contentType": "application/pdf", "tamanhoBytes": 61730, "conteudoBase64": null },
                { "tipo": "NOTA_FISCAL", "nomeOriginal": "NF 2.pdf", "contentType": "application/pdf", "tamanhoBytes": 20480, "conteudoBase64": %s }
              ]
            }
            """.formatted(compraId, quantidade, comNf ? "\"" + nfBase64 + "\"" : "null");
    }

    // -------------------------------------------------------------------------

    @Test
    void semCredencialRetorna401() throws Exception {
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(payload(900, 1, false)))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void perfilIntegracaoNaoAcessaRestoDaApi() throws Exception {
        mvc.perform(get("/api/anexos/1/download").with(httpBasic(LOGIN_INTEGRACAO, SENHA_INTEGRACAO)))
           .andExpect(status().isForbidden());
    }

    @Test
    void payloadInvalidoRetorna400() throws Exception {
        mvc.perform(post(ENDPOINT)
                        .with(httpBasic(LOGIN_INTEGRACAO, SENHA_INTEGRACAO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"origem\": \"ALMOXARIFADO\" }"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.erro").value("Erro de validação"));
    }

    @Test
    void recebimentoCriaPendenciaEReenvioEhIdempotente() throws Exception {
        long compraId = 4242;

        MvcResult primeiro = mvc.perform(post(ENDPOINT)
                        .with(httpBasic(LOGIN_INTEGRACAO, SENHA_INTEGRACAO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(compraId, 1, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andReturn();
        long id = json.readTree(primeiro.getResponse().getContentAsString()).get("id").asLong();

        // reenvio da fila do Almoxarifado → mesma pendência, sem duplicar
        mvc.perform(post(ENDPOINT)
                        .with(httpBasic(LOGIN_INTEGRACAO, SENHA_INTEGRACAO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(compraId, 1, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        assertThat(pendenciaRepo.findByOrigemAndCompraIdOrigem("ALMOXARIFADO", compraId)).isPresent();
        var p = pendenciaRepo.findById(id).orElseThrow();
        assertThat(p.possuiNotaFiscal()).isTrue();
        assertThat(p.getNfNomeOriginal()).isEqualTo("NF 2.pdf");
        assertThat(p.getQuantidadeTotal()).isEqualTo(1);
        assertThat(p.getFornecedor()).isEqualTo("DAVID WELLYNGTON VAZ-ME");
    }

    @Test
    void patrimoniarPreenchePeloRecebimentoAnexaNfEConcluiPendencia() throws Exception {
        long compraId = 5151;
        MvcResult criado = mvc.perform(post(ENDPOINT)
                        .with(httpBasic(LOGIN_INTEGRACAO, SENHA_INTEGRACAO))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(compraId, 2, true)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode corpo = json.readTree(criado.getResponse().getContentAsString());
        long pendenciaId = corpo.get("id").asLong();

        // Lista e sino mostram a pendência
        mvc.perform(get("/pendencias").with(fiscal()))
           .andExpect(status().isOk())
           .andExpect(model().attribute("pendenciasPendentes", org.hamcrest.Matchers.greaterThanOrEqualTo(1L)))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("Impressora multifuncional Samsung")));

        // Formulário pré-preenchido
        MvcResult form = mvc.perform(get("/pendencias/" + pendenciaId + "/patrimoniar").with(fiscal()))
                .andExpect(status().isOk())
                .andExpect(view().name("patrimonios/form"))
                .andReturn();
        var req = (com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest)
                form.getModelAndView().getModel().get("patrimonioForm");
        assertThat(req.descricao()).isEqualTo("Impressora multifuncional Samsung");
        assertThat(req.valorCompra()).isEqualByComparingTo("320.00");
        assertThat(req.notaFiscal()).isEqualTo("00000002");
        assertThat(req.dataCompra()).isEqualTo("2026-10-03");
        assertThat(req.observacao()).contains("Origem: Almoxarifado compra #5151")
                                    .contains("Retirado por Sgt. João Silva")
                                    .contains("Setor 5º BPM - Núcleo de Saúde")
                                    .contains("Parte/Ofício 001/2026")
                                    .contains("Unidade 1 de 2");

        // 1ª unidade → continua pendente, volta para o formulário da 2ª
        mvc.perform(post("/pendencias/" + pendenciaId + "/patrimoniar").with(csrf()).with(fiscal())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("numeroTombo", "T-5151-A")
                        .param("descricao", req.descricao())
                        .param("dataCompra", "2026-10-03")
                        .param("valorCompra", "320.00")
                        .param("notaFiscal", req.notaFiscal())
                        .param("observacao", req.observacao())
                        .param("lotacaoId", lotacaoId.toString())
                        .param("responsavelId", responsavelId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pendencias/" + pendenciaId + "/patrimoniar"));

        var meio = pendenciaRepo.findById(pendenciaId).orElseThrow();
        assertThat(meio.getStatus()).isEqualTo(StatusPendencia.PENDENTE);
        assertThat(meio.getQuantidadePatrimoniada()).isEqualTo(1);

        // 2ª unidade → conclui
        mvc.perform(post("/pendencias/" + pendenciaId + "/patrimoniar").with(csrf()).with(fiscal())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("numeroTombo", "T-5151-B")
                        .param("descricao", req.descricao())
                        .param("dataCompra", "2026-10-03")
                        .param("valorCompra", "320.00")
                        .param("lotacaoId", lotacaoId.toString())
                        .param("responsavelId", responsavelId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pendencias"));

        var fim = pendenciaRepo.findById(pendenciaId).orElseThrow();
        assertThat(fim.getStatus()).isEqualTo(StatusPendencia.CONCLUIDA);
        assertThat(fim.getQuantidadePatrimoniada()).isEqualTo(2);
        assertThat(fim.getConcluidaPor()).isEqualTo("fiscal.teste");
        assertThat(fim.getPatrimonio()).isNotNull();

        // Cada bem recebeu a sua cópia da NF
        var bemB = patrimonioRepo.findByNumeroTombo("T-5151-B").orElseThrow();
        List<ArquivoAnexo> anexosB = anexoRepo.findByPatrimonioId(bemB.getId());
        assertThat(anexosB).hasSize(1);
        assertThat(anexosB.get(0).getTipo()).isEqualTo(TipoAnexo.NOTA_FISCAL);
        assertThat(anexosB.get(0).getNomeOriginal()).isEqualTo("NF 2.pdf");

        // Pendência concluída não aceita novo patrimoniamento
        mvc.perform(get("/pendencias/" + pendenciaId + "/patrimoniar").with(fiscal()))
           .andExpect(status().isOk())
           .andExpect(view().name("erro"));
    }
}

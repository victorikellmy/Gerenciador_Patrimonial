package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Abastecimento;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.ManutencaoVeiculo;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Combustivel;
import com.fundacao.gerenciador_patrimonial.domain.enums.Conservacao;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoServicoVeiculo;
import com.fundacao.gerenciador_patrimonial.dto.request.AbastecimentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.DeslocamentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.ManutencaoVeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.VeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.VeiculoResponse;
import com.fundacao.gerenciador_patrimonial.repository.AbastecimentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.ManutencaoVeiculoRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import com.fundacao.gerenciador_patrimonial.repository.VeiculoRepository;
import com.fundacao.gerenciador_patrimonial.service.storage.StorageService;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

/**
 * Dados de demonstração da frota (homologação): 6 veículos reais da FPTO com
 * ~90 dias de saídas, abastecimentos (com NF em PDF) e manutenções, um veículo
 * na rua e um com revisão vencida, além do usuário {@code motorista.demo}.
 *
 * <p>Ligado por {@code app.frota.seed=true} (perfil dev liga por padrão; em
 * produção use {@code FROTA_SEED=true} só num ambiente de homologação). Só roda
 * quando ainda não existe nenhum veículo.</p>
 */
@Component
@ConditionalOnProperty(name = "app.frota.seed", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
@Order(4)
public class FrotaDemoSeeder implements ApplicationRunner {

    private final VeiculoRepository veiculoRepo;
    private final AbastecimentoRepository abastecimentoRepo;
    private final ManutencaoVeiculoRepository manutencaoRepo;
    private final LotacaoRepository lotacaoRepo;
    private final ResponsavelRepository responsavelRepo;
    private final UsuarioRepository usuarioRepo;
    private final PasswordEncoder encoder;
    private final FrotaService frota;
    private final StorageService storage;

    @Value("${app.frota.seed-senha-motorista:trocar123}")
    private String senhaMotorista;

    private record Carro(String placa, String modelo, String marca, String ano, String cidade, Combustivel comb,
                         int kmInicial, String valor) {}

    private static final List<Carro> CARROS = List.of(
            new Carro("QKJ-7909", "GOL 1.6",        "VOLKSWAGEN", "2018", "PALMAS",     Combustivel.GASOLINA, 58200, "48900.00"),
            new Carro("OYB-8555", "SAVEIRO 1.6",    "VOLKSWAGEN", "2014", "GURUPI",     Combustivel.ETANOL,   131400, "39800.00"),
            new Carro("SGX4J45",  "L200 TRITON",    "MITSUBISHI", "2023", "PALMAS",     Combustivel.DIESEL,   24100, "219900.00"),
            new Carro("TVF-6I91", "AMB JUMPY",      "CITROËN",    "2026", "PALMAS",     Combustivel.DIESEL,   3900,  "289000.00"),
            new Carro("QKJ-7869", "GOL 1.6",        "VOLKSWAGEN", "2018", "ARAGUAINA",  Combustivel.GASOLINA, 74300, "48900.00"),
            new Carro("MWZ-9117", "ODONTOMOVEL",    "IVECO",      "2010", "PALMAS",     Combustivel.DIESEL,   188700, "350000.00"));

    private static final String[] MOTORISTAS = {"Sgt. João Silva", "Cb. Maria Souza", "Sd. Pedro Lima", "Sgt. Ana Costa"};
    private static final String[] DESTINOS = {"HGP - Palmas", "5º BPM - Porto Nacional", "Sede FPTO", "Hospital de Araguaína",
            "QCG - Palmas", "UPA Sul", "Oficina autorizada", "Aeroporto de Palmas", "6º BPM - Taquaralto"};
    private static final String[] MOTIVOS = {"Assistência", "Apoio", "Entregar/buscar Documentos", "Assistência", "Outro"};
    private static final String[] POSTOS = {"Posto Ipiranga - Palmas", "Posto Shell - Gurupi", "Posto BR - Araguaína", "Posto Central"};

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (veiculoRepo.count() > 0) {
            log.info("Seed da frota ignorado: já existem veículos.");
            return;
        }
        Random rnd = new Random(42);
        Responsavel resp = responsavelRepo.findByNomeCompleto(ImportadorTransportesService.RESPONSAVEL_FROTA)
                .orElseGet(() -> responsavelRepo.save(Responsavel.builder()
                        .nomeCompleto(ImportadorTransportesService.RESPONSAVEL_FROTA).ativo(true).build()));
        usuarioRepo.findByLogin("motorista.demo").orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Motorista Demonstração").login("motorista.demo")
                .senhaHash(encoder.encode(senhaMotorista)).perfil(Perfil.MOTORISTA).ativo(true).build()));
        usuarioRepo.findByLogin("gerente.demo").orElseGet(() -> usuarioRepo.save(Usuario.builder()
                .nomeCompleto("Gerente Demonstração").login("gerente.demo")
                .senhaHash(encoder.encode(senhaMotorista)).perfil(Perfil.GERENTE).ativo(true).build()));

        LocalDate hoje = LocalDate.now();
        int indice = 0;
        for (Carro c : CARROS) {
            Lotacao lot = lotacaoRepo.findByUpmAndNome(ImportadorTransportesService.UPM_FROTA, c.cidade())
                    .orElseGet(() -> lotacaoRepo.save(Lotacao.builder()
                            .upm(ImportadorTransportesService.UPM_FROTA).nome(c.cidade()).cidade(c.cidade())
                            .tipoLocal(TipoLocal.INTERNO).build()));
            VeiculoResponse v = frota.criarVeiculo(new VeiculoRequest(null, c.placa(), c.modelo(), c.marca(), null, null,
                    c.ano(), c.cidade(), c.comb(), c.kmInicial(), "VEIC-" + String.format("%04d", 100 + indice),
                    LocalDate.of(Integer.parseInt(c.ano()) > hoje.getYear() ? hoje.getYear() : Integer.parseInt(c.ano()), 3, 10),
                    new BigDecimal(c.valor()), Conservacao.BOM, null,
                    "Veículo da frota FPTO (dados de demonstração)", lot.getId(), resp.getId()));

            int km = c.kmInicial();
            boolean ambulancia = c.modelo().startsWith("AMB");
            // 90 dias de histórico, do mais antigo ao mais recente
            for (int dia = 90; dia >= 1; dia--) {
                LocalDate data = hoje.minusDays(dia);
                if (rnd.nextInt(100) < (ambulancia ? 45 : 30)) {
                    int rodados = 15 + rnd.nextInt(ambulancia ? 160 : 90);
                    LocalTime saida = LocalTime.of(7 + rnd.nextInt(9), rnd.nextInt(4) * 15);
                    frota.registrarDeslocamento(v.id(), new DeslocamentoRequest(v.id(), data,
                            DESTINOS[rnd.nextInt(DESTINOS.length)], MOTIVOS[rnd.nextInt(MOTIVOS.length)],
                            rnd.nextInt(3) == 0 ? "Militar atendido " + (100 + rnd.nextInt(900)) : null, null,
                            saida, km, saida.plusHours(1 + rnd.nextInt(5)), km + rodados,
                            MOTORISTAS[rnd.nextInt(MOTORISTAS.length)], "FPTO - " + c.cidade(), null));
                    km += rodados;
                }
                if (dia % 9 == 0) {
                    BigDecimal litros = BigDecimal.valueOf(25 + rnd.nextInt(30)).setScale(3, RoundingMode.HALF_UP);
                    BigDecimal preco = new BigDecimal(c.comb() == Combustivel.DIESEL ? "6.19" : c.comb() == Combustivel.ETANOL ? "4.39" : "6.49");
                    var a = frota.registrarAbastecimento(v.id(), new AbastecimentoRequest(v.id(), data, km, c.comb(), litros,
                            litros.multiply(preco).setScale(2, RoundingMode.HALF_UP), POSTOS[rnd.nextInt(POSTOS.length)],
                            MOTORISTAS[rnd.nextInt(MOTORISTAS.length)], null));
                    anexarNfDemo(abastecimentoRepo.findById(a.id()).orElseThrow(), v.id(), "abastecimentos",
                            "Abastecimento " + c.placa() + " em " + data, a.valor());
                }
            }

            // Manutenções: troca de óleo no meio do período e uma revisão com próxima programada
            var oleo = frota.registrarManutencao(v.id(), new ManutencaoVeiculoRequest(v.id(), hoje.minusDays(60),
                    TipoServicoVeiculo.TROCA_OLEO, c.kmInicial() + 600, "Troca de óleo e filtro", new BigDecimal("280.00"),
                    "Oficina Boa Vista", "NF 10" + indice, null, null, MOTORISTAS[0]));
            anexarNfDemo(manutencaoRepo.findById(oleo.id()).orElseThrow(), v.id(), "manutencoes",
                    "Troca de óleo " + c.placa(), new BigDecimal("280.00"));

            // veículo 0: revisão vencida (próxima em km já ultrapassado); demais: próxima revisão à frente
            int proxKm = indice == 0 ? c.kmInicial() + 400 : km + 5000;
            var rev = frota.registrarManutencao(v.id(), new ManutencaoVeiculoRequest(v.id(), hoje.minusDays(30),
                    TipoServicoVeiculo.REVISAO, c.kmInicial() + 1500, "Revisão periódica (freios, suspensão, fluidos)",
                    new BigDecimal(indice == 2 ? "1890.00" : "950.00"), "Concessionária " + c.marca(), "NF 20" + indice,
                    proxKm, indice == 0 ? hoje.minusDays(3) : hoje.plusMonths(6), MOTORISTAS[1]));
            anexarNfDemo(manutencaoRepo.findById(rev.id()).orElseThrow(), v.id(), "manutencoes",
                    "Revisão " + c.placa(), rev.valor());

            // veículo 5 (ODONTOMOVEL): na oficina
            if (indice == 5) {
                frota.marcarManutencao(v.id(), true, "Retífica do motor — Oficina Diesel Norte");
            }
            // veículo 2 (L200): saída em andamento hoje, sem chegada
            if (indice == 2) {
                frota.registrarDeslocamento(v.id(), new DeslocamentoRequest(v.id(), hoje, "Hospital de Araguaína",
                        "Assistência", "Militar atendido 377", null, LocalTime.of(6, 30), km, null, null,
                        MOTORISTAS[2], "FPTO - PALMAS", "Transporte de paciente para consulta"));
            }
            indice++;
        }
        log.warn("Seed da frota aplicado: {} veículos com histórico de demonstração; usuário 'motorista.demo' criado.", CARROS.size());
    }

    /** Gera um PDF simples de nota fiscal fictícia e grava como NF do lançamento. */
    private void anexarNfDemo(Object lancamento, Long veiculoId, String pasta, String titulo, BigDecimal valor) {
        byte[] pdf = pdfDemo(titulo, valor);
        String caminho = storage.armazenar(new ByteArrayInputStream(pdf), "nf-demo.pdf", "frota/" + veiculoId + "/" + pasta);
        if (lancamento instanceof Abastecimento a) {
            a.setNfCaminho(caminho); a.setNfNomeOriginal("NF " + titulo + ".pdf");
            a.setNfContentType("application/pdf"); a.setNfTamanhoBytes((long) pdf.length);
            abastecimentoRepo.save(a);
        } else if (lancamento instanceof ManutencaoVeiculo m) {
            m.setNfCaminho(caminho); m.setNfNomeOriginal("NF " + titulo + ".pdf");
            m.setNfContentType("application/pdf"); m.setNfTamanhoBytes((long) pdf.length);
            manutencaoRepo.save(m);
        }
    }

    private static byte[] pdfDemo(String titulo, BigDecimal valor) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("NOTA FISCAL — DOCUMENTO DE DEMONSTRAÇÃO", new Font(Font.HELVETICA, 14, Font.BOLD)));
            doc.add(new Paragraph(titulo));
            doc.add(new Paragraph("Valor: R$ " + valor));
            doc.add(new Paragraph("Gerado automaticamente pelo seed de homologação do Gerenciador Patrimonial."));
            doc.close();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar NF de demonstração", e);
        }
        return out.toByteArray();
    }
}

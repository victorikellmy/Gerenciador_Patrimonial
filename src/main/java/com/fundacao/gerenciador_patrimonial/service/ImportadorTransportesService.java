package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.catalog.FrotaCatalog;
import com.fundacao.gerenciador_patrimonial.domain.entity.Abastecimento;
import com.fundacao.gerenciador_patrimonial.domain.entity.Deslocamento;
import com.fundacao.gerenciador_patrimonial.domain.entity.Lotacao;
import com.fundacao.gerenciador_patrimonial.domain.entity.ManutencaoVeiculo;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Responsavel;
import com.fundacao.gerenciador_patrimonial.domain.entity.Veiculo;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.domain.enums.Combustivel;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoLocal;
import com.fundacao.gerenciador_patrimonial.domain.enums.TipoServicoVeiculo;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.AbastecimentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.DeslocamentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.LotacaoRepository;
import com.fundacao.gerenciador_patrimonial.repository.ManutencaoVeiculoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.ResponsavelRepository;
import com.fundacao.gerenciador_patrimonial.repository.VeiculoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Importa a frota e o histórico do sistema "Transportes FPTO" (banco PostgreSQL
 * {@code transportes} no servidor .25) para o módulo Frota deste sistema.
 *
 * <p>Configuração (variáveis de ambiente):</p>
 * <pre>
 *   TRANSPORTES_DB_URL    = jdbc:postgresql://host.docker.internal:5433/transportes
 *   TRANSPORTES_DB_USER   = transportes
 *   TRANSPORTES_DB_SENHA  = ...
 *   TRANSPORTES_IMPORTAR_NO_BOOT = true   (default quando a URL está definida)
 * </pre>
 *
 * <p>Idempotente: cada registro guarda {@code id_transportes}; rodar de novo
 * não duplica (veículos já importados são apenas mantidos; lançamentos novos
 * no sistema antigo entram). Cada veículo vira um {@link Patrimonio} da categoria
 * VEICULO na lotação {@code FROTA / <cidade>} com o responsável
 * "SETOR DE TRANSPORTES - FPTO" — o administrador ajusta depois.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportadorTransportesService {

    public static final String UPM_FROTA = "FROTA";
    public static final String RESPONSAVEL_FROTA = "SETOR DE TRANSPORTES - FPTO";

    private final VeiculoRepository veiculoRepo;
    private final AbastecimentoRepository abastecimentoRepo;
    private final ManutencaoVeiculoRepository manutencaoRepo;
    private final DeslocamentoRepository deslocamentoRepo;
    private final PatrimonioRepository patrimonioRepo;
    private final LotacaoRepository lotacaoRepo;
    private final ResponsavelRepository responsavelRepo;
    private final PatrimonioService patrimonioService;
    private final AuditoriaService auditoriaService;

    @Value("${app.frota.transportes.url:}")
    private String url;
    @Value("${app.frota.transportes.usuario:transportes}")
    private String usuario;
    @Value("${app.frota.transportes.senha:}")
    private String senha;

    public boolean configurado() {
        return url != null && !url.isBlank();
    }

    public record Resultado(int veiculosNovos, int veiculosExistentes, int abastecimentos, int manutencoes,
                            int deslocamentos, int ignorados) {
        public int total() { return abastecimentos + manutencoes + deslocamentos; }
    }

    /** Conecta no banco do Transportes e importa tudo que ainda não existe aqui. */
    @Transactional
    public Resultado importar() {
        if (!configurado()) {
            throw new RegraDeNegocioException("Importação do Transportes não configurada (TRANSPORTES_DB_URL).");
        }
        try (Connection con = DriverManager.getConnection(url, usuario, senha)) {
            Map<Integer, Veiculo> porIdLegado = new HashMap<>();
            int[] veiculos = importarVeiculos(con, porIdLegado); // novos, existentes
            int[] contadores = new int[4]; // abastecimentos, manutencoes, deslocamentos, ignorados
            importarAbastecimentos(con, porIdLegado, contadores);
            importarManutencoes(con, porIdLegado, contadores);
            importarDeslocamentos(con, porIdLegado, contadores);

            Resultado r = new Resultado(veiculos[0], veiculos[1], contadores[0], contadores[1], contadores[2], contadores[3]);
            auditoriaService.registrar(AcaoAuditoria.FROTA, "Veiculo", null,
                    "Importação do Transportes: %d veículo(s) novo(s), %d já existente(s), %d abastecimento(s), %d manutenção(ões), %d deslocamento(s), %d ignorado(s)"
                            .formatted(r.veiculosNovos(), r.veiculosExistentes(), r.abastecimentos(), r.manutencoes(),
                                       r.deslocamentos(), r.ignorados()));
            log.info("Importação do Transportes concluída: {}", r);
            return r;
        } catch (SQLException e) {
            throw new RegraDeNegocioException("Falha ao ler o banco do Transportes: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ veículos

    /** Preenche {@code mapa} (id no Transportes → veículo) e devolve {novos, existentes}. */
    private int[] importarVeiculos(Connection con, Map<Integer, Veiculo> mapa) throws SQLException {
        int novos = 0;
        int existentes = 0;
        Responsavel responsavel = responsavelFrota();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("select id, placa, modelo, chassi, ano, cidade, ativo from veiculo order by id")) {
            while (rs.next()) {
                int idLegado = rs.getInt("id");
                Veiculo existente = veiculoRepo.findByIdTransportes(idLegado).orElse(null);
                if (existente != null) {
                    mapa.put(idLegado, existente);
                    existentes++;
                    continue;
                }
                String placa = rs.getString("placa").trim().toUpperCase().replaceAll("\\s+", "");
                String modelo = rs.getString("modelo").trim().toUpperCase();
                boolean ativo = rs.getBoolean("ativo");
                String cidade = limpar(rs.getString("cidade"));

                // Duplicados desativados do legado (mesma placa) não viram patrimônio.
                if (!ativo && veiculoRepo.findByPlacaIgnoreCase(placa).isPresent()) {
                    log.info("Transportes: veículo #{} ({}) inativo e duplicado — ignorado.", idLegado, placa);
                    continue;
                }

                Lotacao lotacao = lotacaoFrota(cidade);
                String ano = limpar(rs.getString("ano"));
                LocalDate dataCompra = anoParaData(ano);

                var criado = patrimonioService.criar(new PatrimonioRequest(
                        null,
                        FrotaService.descricaoPatrimonio(modelo, placa),
                        FrotaCatalog.CATEGORIA_VEICULO,
                        null, dataCompra, null, null, null, null, null,
                        "Importado do sistema Transportes FPTO (veículo #" + idLegado + ")",
                        null, lotacao.getId(), responsavel.getId()));

                Veiculo v = Veiculo.builder()
                        .patrimonio(patrimonioRepo.getReferenceById(criado.id()))
                        .placa(placa)
                        .modelo(modelo)
                        .chassi(limpar(rs.getString("chassi")))
                        .ano(ano)
                        .cidade(cidade != null ? cidade.toUpperCase() : null)
                        .ativo(ativo)
                        .idTransportes(idLegado)
                        .build();
                v = veiculoRepo.save(v);
                mapa.put(idLegado, v);
                novos++;
            }
        }
        return new int[]{novos, existentes};
    }

    // ------------------------------------------------------------------ lançamentos

    private void importarAbastecimentos(Connection con, Map<Integer, Veiculo> veiculos, int[] c) throws SQLException {
        String sql = "select a.id, a.veiculo_id, a.km, a.quantidade_litros, a.combustivel, a.valor, a.posto_cidade, " +
                     "a.motorista, a.data, u.login from abastecimento a left join usuario u on u.id = a.usuario_id order by a.id";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int idLegado = rs.getInt("id");
                if (abastecimentoRepo.findByIdTransportes(idLegado).isPresent()) continue;
                Veiculo v = veiculos.get(rs.getInt("veiculo_id"));
                if (v == null) { c[3]++; continue; }
                Abastecimento a = Abastecimento.builder()
                        .veiculo(v)
                        .data(data(rs.getDate("data")))
                        .km(inteiro(rs, "km"))
                        .combustivel(Combustivel.de(rs.getString("combustivel")))
                        .quantidadeLitros(rs.getBigDecimal("quantidade_litros"))
                        .valor(nz(rs.getBigDecimal("valor")))
                        .postoCidade(limpar(rs.getString("posto_cidade")))
                        .motorista(motorista(rs.getString("motorista")))
                        .registradoPor(limpar(rs.getString("login")))
                        .idTransportes(idLegado)
                        .build();
                abastecimentoRepo.save(a);
                v.atualizarKm(a.getKm());
                c[0]++;
            }
        }
    }

    private void importarManutencoes(Connection con, Map<Integer, Veiculo> veiculos, int[] c) throws SQLException {
        String sql = "select m.id, m.veiculo_id, m.servico, m.km, m.data, m.descricao, m.valor, m.motorista, u.login " +
                     "from manutencao m left join usuario u on u.id = m.usuario_id order by m.id";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int idLegado = rs.getInt("id");
                if (manutencaoRepo.findByIdTransportes(idLegado).isPresent()) continue;
                Veiculo v = veiculos.get(rs.getInt("veiculo_id"));
                if (v == null) { c[3]++; continue; }
                ManutencaoVeiculo m = ManutencaoVeiculo.builder()
                        .veiculo(v)
                        .data(data(rs.getDate("data")))
                        .servico(TipoServicoVeiculo.de(rs.getString("servico")))
                        .km(inteiro(rs, "km"))
                        .descricao(truncar(limpar(rs.getString("descricao")), 1000))
                        .valor(nz(rs.getBigDecimal("valor")))
                        .motorista(motorista(rs.getString("motorista")))
                        .registradoPor(limpar(rs.getString("login")))
                        .idTransportes(idLegado)
                        .build();
                manutencaoRepo.save(m);
                v.atualizarKm(m.getKm());
                c[1]++;
            }
        }
    }

    private void importarDeslocamentos(Connection con, Map<Integer, Veiculo> veiculos, int[] c) throws SQLException {
        String sql = "select d.id, d.veiculo_id, d.destino, d.motivo, d.titular, d.assistido, d.hora_saida, d.km_inicial, " +
                     "d.hora_chegada, d.km_final, d.data_viagem, d.motorista, d.lotacao, u.login " +
                     "from deslocamento d left join usuario u on u.id = d.usuario_id order by d.id";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int idLegado = rs.getInt("id");
                if (deslocamentoRepo.findByIdTransportes(idLegado).isPresent()) continue;
                Veiculo v = veiculos.get(rs.getInt("veiculo_id"));
                if (v == null) { c[3]++; continue; }
                Time saida = rs.getTime("hora_saida");
                Time chegada = rs.getTime("hora_chegada");
                Deslocamento d = Deslocamento.builder()
                        .veiculo(v)
                        .dataViagem(data(rs.getDate("data_viagem")))
                        .destino(truncar(limparOu(rs.getString("destino"), "—"), 160))
                        .motivo(truncar(limparOu(rs.getString("motivo"), "Outro"), 60))
                        .titular(truncar(limpar(rs.getString("titular")), 160))
                        .assistido(truncar(limpar(rs.getString("assistido")), 160))
                        .horaSaida(saida != null ? saida.toLocalTime() : null)
                        .kmInicial(inteiro(rs, "km_inicial"))
                        .horaChegada(chegada != null ? chegada.toLocalTime() : null)
                        .kmFinal(inteiro(rs, "km_final"))
                        .motorista(motorista(rs.getString("motorista")))
                        .lotacao(truncar(limpar(rs.getString("lotacao")), 60))
                        .registradoPor(limpar(rs.getString("login")))
                        .idTransportes(idLegado)
                        .build();
                deslocamentoRepo.save(d);
                v.atualizarKm(d.getKmInicial());
                v.atualizarKm(d.getKmFinal());
                c[2]++;
            }
        }
    }

    // ------------------------------------------------------------------ apoio

    private Lotacao lotacaoFrota(String cidade) {
        String nome = cidade != null ? cidade.toUpperCase() : "FPTO";
        return lotacaoRepo.findByUpmAndNome(UPM_FROTA, nome).orElseGet(() -> lotacaoRepo.save(Lotacao.builder()
                .upm(UPM_FROTA)
                .nome(nome)
                .cidade(cidade != null ? cidade.toUpperCase() : null)
                .tipoLocal(TipoLocal.INTERNO)
                .build()));
    }

    private Responsavel responsavelFrota() {
        return responsavelRepo.findByNomeCompleto(RESPONSAVEL_FROTA).orElseGet(() -> responsavelRepo.save(
                Responsavel.builder().nomeCompleto(RESPONSAVEL_FROTA).ativo(true).build()));
    }

    private static LocalDate anoParaData(String ano) {
        if (ano == null) return null;
        try {
            int a = Integer.parseInt(ano.replaceAll("\\D", ""));
            if (a < 1950 || a > LocalDate.now().getYear()) return null;
            return LocalDate.of(a, 1, 1);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate data(Date d) {
        return d != null ? d.toLocalDate() : LocalDate.now();
    }

    private static Integer inteiro(ResultSet rs, String coluna) throws SQLException {
        int v = rs.getInt(coluna);
        return rs.wasNull() ? null : v;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String motorista(String s) {
        String t = limpar(s);
        return t == null ? "NÃO INFORMADO" : truncar(t, 160);
    }

    private static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String limparOu(String s, String padrao) {
        String t = limpar(s);
        return t == null ? padrao : t;
    }

    private static String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}

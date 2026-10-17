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
import java.util.List;
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
                            int deslocamentos, int ignorados, int profissionais, int diarias, int cautelas, int titulares) {
        public int total() { return abastecimentos + manutencoes + deslocamentos + diarias + cautelas; }
    }

    private final com.fundacao.gerenciador_patrimonial.repository.NivelDiariaRepository nivelRepo;
    private final com.fundacao.gerenciador_patrimonial.repository.ProfissionalRepository profissionalRepo;
    private final com.fundacao.gerenciador_patrimonial.repository.DiariaRepository diariaRepo;
    private final com.fundacao.gerenciador_patrimonial.repository.DiariaHistoricoRepository historicoRepo;
    private final com.fundacao.gerenciador_patrimonial.repository.CautelaRepository cautelaRepo;
    private final com.fundacao.gerenciador_patrimonial.repository.TitularRepository titularRepo;

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
            int[] extras = new int[4]; // profissionais, diarias, cautelas, titulares
            Map<Integer, com.fundacao.gerenciador_patrimonial.domain.entity.NivelDiaria> niveis = importarNiveis(con);
            Map<Integer, com.fundacao.gerenciador_patrimonial.domain.entity.Profissional> profissionais = importarProfissionais(con, niveis, extras);
            importarDiarias(con, profissionais, extras);
            importarCautelas(con, porIdLegado, extras);
            importarTitulares(con, extras);

            Resultado r = new Resultado(veiculos[0], veiculos[1], contadores[0], contadores[1], contadores[2], contadores[3],
                    extras[0], extras[1], extras[2], extras[3]);
            auditoriaService.registrar(AcaoAuditoria.FROTA, "Veiculo", null,
                    ("Importação do Transportes: %d veículo(s) novo(s), %d já existente(s), %d abastecimento(s), %d manutenção(ões), " +
                     "%d deslocamento(s), %d ignorado(s), %d profissional(is), %d diária(s), %d cautela(s), %d titular(es)")
                            .formatted(r.veiculosNovos(), r.veiculosExistentes(), r.abastecimentos(), r.manutencoes(),
                                       r.deslocamentos(), r.ignorados(), r.profissionais(), r.diarias(), r.cautelas(), r.titulares()));
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

    // ------------------------------------------------------------------ diárias / cautelas / titulares

    private Map<Integer, com.fundacao.gerenciador_patrimonial.domain.entity.NivelDiaria> importarNiveis(Connection con) throws SQLException {
        Map<Integer, com.fundacao.gerenciador_patrimonial.domain.entity.NivelDiaria> mapa = new HashMap<>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("select id, descricao, valor_capitais, valor_interior_estado, valor_interior_outro_estado, ativo from nivel_diaria order by id")) {
            while (rs.next()) {
                int idLegado = rs.getInt("id");
                var existente = nivelRepo.findByIdTransportes(idLegado).orElse(null);
                if (existente == null) {
                    existente = nivelRepo.save(com.fundacao.gerenciador_patrimonial.domain.entity.NivelDiaria.builder()
                            .descricao(truncar(limparOu(rs.getString("descricao"), "Nível " + idLegado), 160))
                            .valorCapitais(nz(rs.getBigDecimal("valor_capitais")))
                            .valorInteriorEstado(nz(rs.getBigDecimal("valor_interior_estado")))
                            .valorInteriorOutroEstado(nz(rs.getBigDecimal("valor_interior_outro_estado")))
                            .ativo(rs.getBoolean("ativo"))
                            .idTransportes(idLegado)
                            .build());
                }
                mapa.put(idLegado, existente);
            }
        }
        return mapa;
    }

    private Map<Integer, com.fundacao.gerenciador_patrimonial.domain.entity.Profissional> importarProfissionais(
            Connection con, Map<Integer, com.fundacao.gerenciador_patrimonial.domain.entity.NivelDiaria> niveis, int[] c) throws SQLException {
        Map<Integer, com.fundacao.gerenciador_patrimonial.domain.entity.Profissional> mapa = new HashMap<>();
        String sql = "select id, matricula, nome, lotacao, nivel_diaria_id, cpf, rg, orgao_expedidor, dados_bancarios, ativo, cadastrado_por from profissional order by id";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int idLegado = rs.getInt("id");
                var existente = profissionalRepo.findByIdTransportes(idLegado).orElse(null);
                if (existente == null) {
                    var nivel = niveis.get(rs.getInt("nivel_diaria_id"));
                    if (nivel == null) continue;
                    String cpf = DiariaService.formatarCpf(rs.getString("cpf"));
                    existente = profissionalRepo.save(com.fundacao.gerenciador_patrimonial.domain.entity.Profissional.builder()
                            .matricula(truncar(limpar(rs.getString("matricula")), 30))
                            .nome(truncar(limparOu(rs.getString("nome"), "SEM NOME").toUpperCase(), 160))
                            .lotacao(truncar(limpar(rs.getString("lotacao")), 60))
                            .nivel(nivel)
                            .cpf(cpf != null ? cpf : truncar(limparOu(rs.getString("cpf"), "000.000.000-00"), 14))
                            .rg(truncar(limpar(rs.getString("rg")), 30))
                            .orgaoExpedidor(truncar(limpar(rs.getString("orgao_expedidor")), 30))
                            .dadosBancarios(truncar(limpar(rs.getString("dados_bancarios")), 160))
                            .ativo(rs.getBoolean("ativo"))
                            .cadastradoPor(truncar(limpar(rs.getString("cadastrado_por")), 160))
                            .idTransportes(idLegado)
                            .build());
                    c[0]++;
                }
                mapa.put(idLegado, existente);
            }
        }
        return mapa;
    }

    private void importarDiarias(Connection con, Map<Integer, com.fundacao.gerenciador_patrimonial.domain.entity.Profissional> profissionais, int[] c) throws SQLException {
        String sql = "select id, data_solicitacao, profissional_id, itinerario, hora_saida, meio_transporte, partida, retorno, hora_chegada, " +
                     "recurso, numero_diarias, valor_unitario, valor_total, finalidade, solicitante, status, data_pagamento, motivo_indeferimento, criado_em " +
                     "from diaria order by id";
        Map<Integer, com.fundacao.gerenciador_patrimonial.domain.entity.Diaria> novas = new HashMap<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int idLegado = rs.getInt("id");
                if (diariaRepo.findByIdTransportes(idLegado).isPresent()) continue;
                var prof = profissionais.get(rs.getInt("profissional_id"));
                if (prof == null) continue;
                Time saida = rs.getTime("hora_saida");
                Time chegada = rs.getTime("hora_chegada");
                java.sql.Timestamp criado = rs.getTimestamp("criado_em");
                BigDecimal nDiarias = nz(rs.getBigDecimal("numero_diarias"));
                BigDecimal unit = nz(rs.getBigDecimal("valor_unitario"));
                BigDecimal total = rs.getBigDecimal("valor_total");
                var d = com.fundacao.gerenciador_patrimonial.domain.entity.Diaria.builder()
                        .dataSolicitacao(data(rs.getDate("data_solicitacao")))
                        .profissional(prof)
                        .itinerario(truncar(limparOu(rs.getString("itinerario"), "—"), 200))
                        .horaSaida(saida != null ? saida.toLocalTime() : null)
                        .meioTransporte(com.fundacao.gerenciador_patrimonial.domain.enums.MeioTransporte.de(rs.getString("meio_transporte")))
                        .partida(data(rs.getDate("partida")))
                        .retorno(data(rs.getDate("retorno")))
                        .horaChegada(chegada != null ? chegada.toLocalTime() : null)
                        .recurso(com.fundacao.gerenciador_patrimonial.domain.enums.RecursoDiaria.de(rs.getString("recurso")))
                        .numeroDiarias(nDiarias)
                        .valorUnitario(unit)
                        .valorTotal(total != null ? total : nDiarias.multiply(unit))
                        .finalidade(truncar(limpar(rs.getString("finalidade")), 2000))
                        .solicitante(truncar(limparOu(rs.getString("solicitante"), "Transportes FPTO"), 160))
                        .status(com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria.de(rs.getString("status")))
                        .dataPagamento(rs.getDate("data_pagamento") != null ? rs.getDate("data_pagamento").toLocalDate() : null)
                        .motivoIndeferimento(truncar(limpar(rs.getString("motivo_indeferimento")), 500))
                        .criadoEm(criado != null ? criado.toLocalDateTime() : null)
                        .idTransportes(idLegado)
                        .build();
                novas.put(idLegado, diariaRepo.save(d));
                c[1]++;
            }
        }
        if (novas.isEmpty()) return;
        String hist = "select diaria_id, de_status, para_status, usuario, instante, observacao from diaria_historico order by id";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(hist)) {
            while (rs.next()) {
                var d = novas.get(rs.getInt("diaria_id"));
                if (d == null) continue;
                java.sql.Timestamp inst = rs.getTimestamp("instante");
                historicoRepo.save(com.fundacao.gerenciador_patrimonial.domain.entity.DiariaHistorico.builder()
                        .diaria(d)
                        .deStatus(rs.getString("de_status") != null ? com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria.de(rs.getString("de_status")) : null)
                        .paraStatus(com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria.de(rs.getString("para_status")))
                        .usuario(truncar(limparOu(rs.getString("usuario"), "Transportes FPTO"), 160))
                        .instante(inst != null ? inst.toLocalDateTime() : LocalDate.now().atStartOfDay())
                        .observacao(truncar(limpar(rs.getString("observacao")), 500))
                        .build());
            }
        }
    }

    private void importarCautelas(Connection con, Map<Integer, Veiculo> veiculos, int[] c) throws SQLException {
        String sql = "select c.id, c.veiculo_id, c.destino, c.motivo, c.responsavel, c.assistido, c.hora_saida, c.km_inicial, c.hora_chegada, c.km_final, " +
                     "c.data_viagem, c.motorista_nome, c.rg, c.cpf, c.fone, c.lotacao, u.login from cautela c left join usuario u on u.id = c.usuario_id order by c.id";
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int idLegado = rs.getInt("id");
                if (cautelaRepo.findByIdTransportes(idLegado).isPresent()) continue;
                Veiculo v = veiculos.get(rs.getInt("veiculo_id"));
                if (v == null) continue;
                Time saida = rs.getTime("hora_saida");
                Time chegada = rs.getTime("hora_chegada");
                cautelaRepo.save(com.fundacao.gerenciador_patrimonial.domain.entity.Cautela.builder()
                        .veiculo(v)
                        .dataViagem(data(rs.getDate("data_viagem")))
                        .destino(truncar(limparOu(rs.getString("destino"), "—"), 160))
                        .motivo(truncar(limparOu(rs.getString("motivo"), "Cautela"), 60))
                        .responsavel(truncar(limpar(rs.getString("responsavel")), 160))
                        .assistido(truncar(limpar(rs.getString("assistido")), 160))
                        .horaSaida(saida != null ? saida.toLocalTime() : null)
                        .kmInicial(inteiro(rs, "km_inicial"))
                        .horaChegada(chegada != null ? chegada.toLocalTime() : null)
                        .kmFinal(inteiro(rs, "km_final"))
                        .motoristaNome(truncar(limparOu(rs.getString("motorista_nome"), "NÃO INFORMADO"), 160))
                        .rg(truncar(limpar(rs.getString("rg")), 30))
                        .cpf(truncar(limpar(rs.getString("cpf")), 14))
                        .fone(truncar(limpar(rs.getString("fone")), 25))
                        .lotacao(truncar(limpar(rs.getString("lotacao")), 60))
                        .registradoPor(limpar(rs.getString("login")))
                        .idTransportes(idLegado)
                        .build());
                c[2]++;
            }
        }
    }

    private void importarTitulares(Connection con, int[] c) throws SQLException {
        java.util.Set<Integer> existentes = new java.util.HashSet<>();
        for (var t : titularRepo.findAll()) if (t.getIdTransportes() != null) existentes.add(t.getIdTransportes());
        List<com.fundacao.gerenciador_patrimonial.domain.entity.Titular> lote = new java.util.ArrayList<>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("select id, posto_graduacao, tipo_cadastro, matricula, vinculo, nome, cpf from titular order by id")) {
            while (rs.next()) {
                int idLegado = rs.getInt("id");
                if (existentes.contains(idLegado)) continue;
                String nome = limpar(rs.getString("nome"));
                if (nome == null) continue;
                lote.add(com.fundacao.gerenciador_patrimonial.domain.entity.Titular.builder()
                        .postoGraduacao(inteiro(rs, "posto_graduacao"))
                        .tipoCadastro(truncar(limpar(rs.getString("tipo_cadastro")), 1))
                        .matricula(truncar(limpar(rs.getString("matricula")), 20))
                        .vinculo(truncar(limpar(rs.getString("vinculo")), 2))
                        .nome(truncar(nome, 120))
                        .cpf(truncar(limpar(rs.getString("cpf")), 15))
                        .idTransportes(idLegado)
                        .build());
                if (lote.size() >= 500) { titularRepo.saveAll(lote); c[3] += lote.size(); lote.clear(); }
            }
        }
        if (!lote.isEmpty()) { titularRepo.saveAll(lote); c[3] += lote.size(); }
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

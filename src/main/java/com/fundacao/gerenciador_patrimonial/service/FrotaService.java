package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.catalog.FrotaCatalog;
import com.fundacao.gerenciador_patrimonial.domain.entity.Abastecimento;
import com.fundacao.gerenciador_patrimonial.domain.entity.Deslocamento;
import com.fundacao.gerenciador_patrimonial.domain.entity.ManutencaoVeiculo;
import com.fundacao.gerenciador_patrimonial.domain.entity.Patrimonio;
import com.fundacao.gerenciador_patrimonial.domain.entity.Veiculo;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.dto.request.AbastecimentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.DeslocamentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroFrota;
import com.fundacao.gerenciador_patrimonial.dto.request.FinalizarDeslocamentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.ManutencaoVeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.PatrimonioRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.VeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.AbastecimentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.DeslocamentoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.FrotaResumo;
import com.fundacao.gerenciador_patrimonial.dto.response.HistoricoVeiculoItem;
import com.fundacao.gerenciador_patrimonial.dto.response.HistoricoVeiculoItem.Tipo;
import com.fundacao.gerenciador_patrimonial.dto.response.ManutencaoVeiculoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.PatrimonioResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.VeiculoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.VeiculoResumo;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.AbastecimentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.DeslocamentoRepository;
import com.fundacao.gerenciador_patrimonial.repository.ManutencaoVeiculoRepository;
import com.fundacao.gerenciador_patrimonial.repository.PatrimonioRepository;
import com.fundacao.gerenciador_patrimonial.repository.VeiculoRepository;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Frota: veículos (sempre um {@link Patrimonio} de categoria VEICULO) e o
 * histórico operacional de cada um — abastecimentos, manutenções/revisões e
 * deslocamentos. Todo lançamento com odômetro avança o {@code kmAtual} do veículo.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FrotaService {

    private static final String ENT_VEICULO = "Veiculo";

    private final VeiculoRepository veiculoRepo;
    private final AbastecimentoRepository abastecimentoRepo;
    private final ManutencaoVeiculoRepository manutencaoRepo;
    private final DeslocamentoRepository deslocamentoRepo;
    private final PatrimonioRepository patrimonioRepo;
    private final PatrimonioService patrimonioService;
    private final AuditoriaService auditoriaService;

    // =========================================================================
    // Veículos
    // =========================================================================

    @Transactional(readOnly = true)
    public List<VeiculoResponse> listarVeiculos(boolean somenteAtivos) {
        return veiculoRepo.listarComPatrimonio().stream()
                .filter(v -> !somenteAtivos || v.isAtivo())
                .map(this::toResponse)
                .toList();
    }

    /** Veículos ativos para selects dos lançamentos. */
    @Transactional(readOnly = true)
    public List<VeiculoResponse> veiculosParaSelect() {
        return veiculoRepo.findByAtivoTrueOrderByPlacaAsc().stream()
                .map(v -> VeiculoResponse.from(v, false, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public VeiculoResponse buscarVeiculo(Long id) {
        return toResponse(carregarVeiculo(id));
    }

    @Transactional(readOnly = true)
    public Optional<VeiculoResponse> buscarPorPatrimonio(Long patrimonioId) {
        return veiculoRepo.findByPatrimonioId(patrimonioId).map(this::toResponse);
    }

    /** Patrimônios da categoria VEICULO ainda sem ficha de veículo (para vincular). */
    @Transactional(readOnly = true)
    public List<PatrimonioResponse> patrimoniosVeiculoSemFicha() {
        return patrimonioRepo.findAll().stream()
                .filter(p -> FrotaCatalog.CATEGORIA_VEICULO.equalsIgnoreCase(p.getCategoria()))
                .filter(p -> veiculoRepo.findByPatrimonioId(p.getId()).isEmpty())
                .sorted(Comparator.comparing(Patrimonio::getDescricao, String.CASE_INSENSITIVE_ORDER))
                .map(patrimonioService::toResponse)
                .toList();
    }

    public VeiculoResponse criarVeiculo(VeiculoRequest req) {
        String placa = normalizarPlaca(req.placa());
        validarPlacaUnica(placa, null);

        Patrimonio patrimonio;
        if (req.patrimonioId() != null) {
            patrimonio = patrimonioRepo.findById(req.patrimonioId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Patrimônio", req.patrimonioId()));
            if (veiculoRepo.findByPatrimonioId(patrimonio.getId()).isPresent()) {
                throw new RegraDeNegocioException("Este patrimônio já está vinculado a outro veículo.");
            }
            // Garante a categoria e sincroniza a descrição com placa/modelo.
            patrimonioService.atualizar(patrimonio.getId(),
                    requestDePatrimonio(patrimonioService.toResponse(patrimonio), req, placa));
        } else {
            if (req.lotacaoId() == null || req.responsavelId() == null) {
                throw new RegraDeNegocioException("Informe a lotação e o responsável do veículo (dados do patrimônio).");
            }
            PatrimonioResponse criado = patrimonioService.criar(new PatrimonioRequest(
                    req.numeroTombo(),
                    descricaoPatrimonio(req.modelo(), placa),
                    FrotaCatalog.CATEGORIA_VEICULO,
                    null,
                    req.dataCompra(),
                    req.valorCompra(),
                    req.conservacao(),
                    req.notaFiscal(),
                    null, null,
                    req.observacao(),
                    null,
                    req.lotacaoId(),
                    req.responsavelId()));
            patrimonio = patrimonioRepo.getReferenceById(criado.id());
        }

        Veiculo v = Veiculo.builder()
                .patrimonio(patrimonio)
                .placa(placa)
                .modelo(req.modelo().trim().toUpperCase())
                .marca(upper(req.marca()))
                .chassi(upper(req.chassi()))
                .renavam(limpar(req.renavam()))
                .ano(limpar(req.ano()))
                .cidade(upper(req.cidade()))
                .combustivelPadrao(req.combustivelPadrao())
                .kmAtual(req.kmAtual())
                .ativo(true)
                .build();
        v = veiculoRepo.save(v);

        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, v.getId(),
                "Veículo cadastrado: %s (patrimônio #%d)".formatted(v.getRotulo(), patrimonio.getId()));
        return toResponse(carregarVeiculo(v.getId()));
    }

    public VeiculoResponse atualizarVeiculo(Long id, VeiculoRequest req) {
        Veiculo v = carregarVeiculo(id);
        String placa = normalizarPlaca(req.placa());
        validarPlacaUnica(placa, id);

        v.setPlaca(placa);
        v.setModelo(req.modelo().trim().toUpperCase());
        v.setMarca(upper(req.marca()));
        v.setChassi(upper(req.chassi()));
        v.setRenavam(limpar(req.renavam()));
        v.setAno(limpar(req.ano()));
        v.setCidade(upper(req.cidade()));
        v.setCombustivelPadrao(req.combustivelPadrao());
        if (req.kmAtual() != null) v.setKmAtual(req.kmAtual());

        // Mantém o patrimônio coerente (descrição, tombo, lotação, responsável, valores).
        PatrimonioResponse atual = patrimonioService.toResponse(v.getPatrimonio());
        patrimonioService.atualizar(atual.id(), requestDePatrimonio(atual, req, placa));

        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, v.getId(), "Veículo atualizado: " + v.getRotulo());
        return toResponse(v);
    }

    public void alterarAtivo(Long id, boolean ativo) {
        Veiculo v = carregarVeiculo(id);
        v.setAtivo(ativo);
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, id,
                (ativo ? "Veículo reativado: " : "Veículo desativado: ") + v.getRotulo());
    }

    // =========================================================================
    // Abastecimentos
    // =========================================================================

    public AbastecimentoResponse registrarAbastecimento(Long veiculoId, AbastecimentoRequest req) {
        validarData(req.data());
        Veiculo v = carregarVeiculo(veiculoId);
        Abastecimento a = Abastecimento.builder().veiculo(v).registradoPor(loginAtual()).build();
        aplicar(a, req);
        a = abastecimentoRepo.save(a);
        v.atualizarKm(a.getKm());
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, v.getId(),
                "Abastecimento #%d: %s, %s L, R$ %s, km %s".formatted(a.getId(), a.getCombustivel(),
                        a.getQuantidadeLitros(), a.getValor(), a.getKm()));
        return AbastecimentoResponse.from(a);
    }

    public AbastecimentoResponse atualizarAbastecimento(Long id, AbastecimentoRequest req) {
        validarData(req.data());
        Abastecimento a = abastecimentoRepo.findComVeiculo(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Abastecimento", id));
        aplicar(a, req);
        a.getVeiculo().atualizarKm(a.getKm());
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, a.getVeiculo().getId(),
                "Abastecimento #%d alterado".formatted(id));
        return AbastecimentoResponse.from(a);
    }

    public void excluirAbastecimento(Long id) {
        Abastecimento a = abastecimentoRepo.findComVeiculo(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Abastecimento", id));
        Long veiculoId = a.getVeiculo().getId();
        abastecimentoRepo.delete(a);
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, veiculoId, "Abastecimento #%d excluído".formatted(id));
    }

    @Transactional(readOnly = true)
    public AbastecimentoResponse buscarAbastecimento(Long id) {
        return abastecimentoRepo.findComVeiculo(id).map(AbastecimentoResponse::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Abastecimento", id));
    }

    @Transactional(readOnly = true)
    public Page<AbastecimentoResponse> listarAbastecimentos(FiltroFrota f, Pageable pageable) {
        Specification<Abastecimento> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) root.fetch("veiculo");
            List<Predicate> ps = new ArrayList<>();
            if (f.veiculoId() != null) ps.add(cb.equal(root.get("veiculo").get("id"), f.veiculoId()));
            if (f.de() != null) ps.add(cb.greaterThanOrEqualTo(root.get("data"), f.de()));
            if (f.ate() != null) ps.add(cb.lessThanOrEqualTo(root.get("data"), f.ate()));
            String t = f.textoOuNulo();
            if (t != null) {
                String like = "%" + t + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("postoCidade")), like),
                        cb.like(cb.lower(root.get("motorista")), like),
                        cb.like(cb.lower(root.get("veiculo").get("placa")), like),
                        cb.like(cb.lower(root.get("veiculo").get("modelo")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return abastecimentoRepo.findAll(spec, pageable).map(AbastecimentoResponse::from);
    }

    @Transactional(readOnly = true)
    public List<AbastecimentoResponse> abastecimentosDoVeiculo(Long veiculoId) {
        return abastecimentoRepo.findByVeiculoIdOrderByDataDescIdDesc(veiculoId).stream()
                .map(AbastecimentoResponse::from).toList();
    }

    private void aplicar(Abastecimento a, AbastecimentoRequest req) {
        a.setData(req.data());
        a.setKm(req.km());
        a.setCombustivel(req.combustivel());
        a.setQuantidadeLitros(req.quantidadeLitros());
        a.setValor(req.valor());
        a.setPostoCidade(limpar(req.postoCidade()));
        a.setMotorista(req.motorista().trim());
        a.setObservacao(limpar(req.observacao()));
    }

    // =========================================================================
    // Manutenções / revisões
    // =========================================================================

    public ManutencaoVeiculoResponse registrarManutencao(Long veiculoId, ManutencaoVeiculoRequest req) {
        validarData(req.data());
        Veiculo v = carregarVeiculo(veiculoId);
        ManutencaoVeiculo m = ManutencaoVeiculo.builder().veiculo(v).registradoPor(loginAtual()).build();
        aplicar(m, req);
        m = manutencaoRepo.save(m);
        v.atualizarKm(m.getKm());
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, v.getId(),
                "Manutenção #%d: %s, R$ %s, km %s".formatted(m.getId(), m.getServico(), m.getValor(), m.getKm()));
        return ManutencaoVeiculoResponse.from(m);
    }

    public ManutencaoVeiculoResponse atualizarManutencao(Long id, ManutencaoVeiculoRequest req) {
        validarData(req.data());
        ManutencaoVeiculo m = manutencaoRepo.findComVeiculo(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Manutenção", id));
        aplicar(m, req);
        m.getVeiculo().atualizarKm(m.getKm());
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, m.getVeiculo().getId(),
                "Manutenção #%d alterada".formatted(id));
        return ManutencaoVeiculoResponse.from(m);
    }

    public void excluirManutencao(Long id) {
        ManutencaoVeiculo m = manutencaoRepo.findComVeiculo(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Manutenção", id));
        Long veiculoId = m.getVeiculo().getId();
        manutencaoRepo.delete(m);
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, veiculoId, "Manutenção #%d excluída".formatted(id));
    }

    @Transactional(readOnly = true)
    public ManutencaoVeiculoResponse buscarManutencao(Long id) {
        return manutencaoRepo.findComVeiculo(id).map(ManutencaoVeiculoResponse::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Manutenção", id));
    }

    @Transactional(readOnly = true)
    public Page<ManutencaoVeiculoResponse> listarManutencoes(FiltroFrota f, Pageable pageable) {
        Specification<ManutencaoVeiculo> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) root.fetch("veiculo");
            List<Predicate> ps = new ArrayList<>();
            if (f.veiculoId() != null) ps.add(cb.equal(root.get("veiculo").get("id"), f.veiculoId()));
            if (f.de() != null) ps.add(cb.greaterThanOrEqualTo(root.get("data"), f.de()));
            if (f.ate() != null) ps.add(cb.lessThanOrEqualTo(root.get("data"), f.ate()));
            String t = f.textoOuNulo();
            if (t != null) {
                String like = "%" + t + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("descricao")), like),
                        cb.like(cb.lower(root.get("fornecedor")), like),
                        cb.like(cb.lower(root.get("motorista")), like),
                        cb.like(cb.lower(root.get("veiculo").get("placa")), like),
                        cb.like(cb.lower(root.get("veiculo").get("modelo")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return manutencaoRepo.findAll(spec, pageable).map(ManutencaoVeiculoResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ManutencaoVeiculoResponse> manutencoesDoVeiculo(Long veiculoId) {
        return manutencaoRepo.findByVeiculoIdOrderByDataDescIdDesc(veiculoId).stream()
                .map(ManutencaoVeiculoResponse::from).toList();
    }

    private void aplicar(ManutencaoVeiculo m, ManutencaoVeiculoRequest req) {
        m.setData(req.data());
        m.setServico(req.servico());
        m.setKm(req.km());
        m.setDescricao(limpar(req.descricao()));
        m.setValor(req.valor() == null ? BigDecimal.ZERO : req.valor());
        m.setFornecedor(limpar(req.fornecedor()));
        m.setNotaFiscal(limpar(req.notaFiscal()));
        m.setProximaRevisaoKm(req.proximaRevisaoKm());
        m.setProximaRevisaoData(req.proximaRevisaoData());
        m.setMotorista(req.motorista().trim());
    }

    // =========================================================================
    // Deslocamentos
    // =========================================================================

    public DeslocamentoResponse registrarDeslocamento(Long veiculoId, DeslocamentoRequest req) {
        validarDeslocamento(req);
        Veiculo v = carregarVeiculo(veiculoId);
        if (req.kmFinal() == null && deslocamentoRepo.findFirstByVeiculoIdAndKmFinalIsNullOrderByDataViagemDescIdDesc(veiculoId).isPresent()) {
            throw new RegraDeNegocioException("Este veículo já tem uma saída em andamento. Finalize-a antes de registrar outra.");
        }
        Deslocamento d = Deslocamento.builder().veiculo(v).registradoPor(loginAtual()).build();
        aplicar(d, req);
        d = deslocamentoRepo.save(d);
        v.atualizarKm(d.getKmInicial());
        v.atualizarKm(d.getKmFinal());
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, v.getId(),
                "Saída #%d: %s (%s), km %s%s".formatted(d.getId(), d.getDestino(), d.getMotivo(), d.getKmInicial(),
                        d.isEmAndamento() ? " — em andamento" : ""));
        return DeslocamentoResponse.from(d);
    }

    public DeslocamentoResponse atualizarDeslocamento(Long id, DeslocamentoRequest req) {
        validarDeslocamento(req);
        Deslocamento d = deslocamentoRepo.findComVeiculo(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Deslocamento", id));
        aplicar(d, req);
        d.getVeiculo().atualizarKm(d.getKmInicial());
        d.getVeiculo().atualizarKm(d.getKmFinal());
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, d.getVeiculo().getId(),
                "Saída #%d alterada".formatted(id));
        return DeslocamentoResponse.from(d);
    }

    /** Registra a chegada (hora e km final) de uma saída em andamento. */
    public DeslocamentoResponse finalizarDeslocamento(Long id, FinalizarDeslocamentoRequest req) {
        Deslocamento d = deslocamentoRepo.findComVeiculo(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Deslocamento", id));
        if (!d.isEmAndamento()) {
            throw new RegraDeNegocioException("Esta saída já foi finalizada.");
        }
        if (d.getKmInicial() != null && req.kmFinal() < d.getKmInicial()) {
            throw new RegraDeNegocioException("Km final não pode ser menor que o km inicial (" + d.getKmInicial() + ").");
        }
        d.setKmFinal(req.kmFinal());
        d.setHoraChegada(req.horaChegada());
        d.getVeiculo().atualizarKm(req.kmFinal());
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, d.getVeiculo().getId(),
                "Saída #%d finalizada: km final %d (%s km rodados)".formatted(id, d.getKmFinal(),
                        d.getKmRodados() != null ? d.getKmRodados() : "?"));
        return DeslocamentoResponse.from(d);
    }

    public void excluirDeslocamento(Long id) {
        Deslocamento d = deslocamentoRepo.findComVeiculo(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Deslocamento", id));
        Long veiculoId = d.getVeiculo().getId();
        deslocamentoRepo.delete(d);
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, veiculoId, "Saída #%d excluída".formatted(id));
    }

    @Transactional(readOnly = true)
    public DeslocamentoResponse buscarDeslocamento(Long id) {
        return deslocamentoRepo.findComVeiculo(id).map(DeslocamentoResponse::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Deslocamento", id));
    }

    @Transactional(readOnly = true)
    public Page<DeslocamentoResponse> listarDeslocamentos(FiltroFrota f, Pageable pageable) {
        Specification<Deslocamento> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) root.fetch("veiculo");
            List<Predicate> ps = new ArrayList<>();
            if (f.veiculoId() != null) ps.add(cb.equal(root.get("veiculo").get("id"), f.veiculoId()));
            if (f.de() != null) ps.add(cb.greaterThanOrEqualTo(root.get("dataViagem"), f.de()));
            if (f.ate() != null) ps.add(cb.lessThanOrEqualTo(root.get("dataViagem"), f.ate()));
            if (Boolean.TRUE.equals(f.somenteAbertos())) ps.add(cb.isNull(root.get("kmFinal")));
            String t = f.textoOuNulo();
            if (t != null) {
                String like = "%" + t + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("destino")), like),
                        cb.like(cb.lower(root.get("motivo")), like),
                        cb.like(cb.lower(root.get("titular")), like),
                        cb.like(cb.lower(root.get("motorista")), like),
                        cb.like(cb.lower(root.get("lotacao")), like),
                        cb.like(cb.lower(root.get("veiculo").get("placa")), like),
                        cb.like(cb.lower(root.get("veiculo").get("modelo")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return deslocamentoRepo.findAll(spec, pageable).map(DeslocamentoResponse::from);
    }

    @Transactional(readOnly = true)
    public List<DeslocamentoResponse> deslocamentosDoVeiculo(Long veiculoId) {
        return deslocamentoRepo.findByVeiculoIdOrderByDataViagemDescIdDesc(veiculoId).stream()
                .map(DeslocamentoResponse::from).toList();
    }

    private void validarDeslocamento(DeslocamentoRequest req) {
        if (req.dataViagem() != null && req.dataViagem().isAfter(LocalDate.now().plusDays(1))) {
            throw new RegraDeNegocioException("A data da viagem não pode estar no futuro.");
        }
        if (req.kmInicial() != null && req.kmFinal() != null && req.kmFinal() < req.kmInicial()) {
            throw new RegraDeNegocioException("Km final não pode ser menor que o km inicial.");
        }
    }

    private void aplicar(Deslocamento d, DeslocamentoRequest req) {
        d.setDataViagem(req.dataViagem());
        d.setDestino(req.destino().trim());
        d.setMotivo(req.motivo().trim());
        d.setTitular(limpar(req.titular()));
        d.setAssistido(limpar(req.assistido()));
        d.setHoraSaida(req.horaSaida());
        d.setKmInicial(req.kmInicial());
        d.setHoraChegada(req.horaChegada());
        d.setKmFinal(req.kmFinal());
        d.setMotorista(req.motorista().trim());
        d.setLotacao(limpar(req.lotacao()));
        d.setObservacao(limpar(req.observacao()));
    }

    // =========================================================================
    // Histórico e resumos
    // =========================================================================

    /** Histórico unificado do veículo, do mais recente para o mais antigo. */
    @Transactional(readOnly = true)
    public List<HistoricoVeiculoItem> historico(Long veiculoId) {
        List<HistoricoVeiculoItem> itens = new ArrayList<>();
        for (Abastecimento a : abastecimentoRepo.findByVeiculoIdOrderByDataDescIdDesc(veiculoId)) {
            String detalhe = (a.getQuantidadeLitros() != null ? a.getQuantidadeLitros().stripTrailingZeros().toPlainString() + " L · " : "")
                    + (a.getPostoCidade() != null ? a.getPostoCidade() : "");
            itens.add(new HistoricoVeiculoItem(Tipo.ABASTECIMENTO, a.getId(), a.getData(), null,
                    "Abastecimento · " + a.getCombustivel().getRotulo(), detalhe.isBlank() ? null : detalhe,
                    a.getKm(), a.getValor(), a.getMotorista(), false));
        }
        for (ManutencaoVeiculo m : manutencaoRepo.findByVeiculoIdOrderByDataDescIdDesc(veiculoId)) {
            String detalhe = (m.getDescricao() != null ? m.getDescricao() : "")
                    + (m.getFornecedor() != null ? " · " + m.getFornecedor() : "")
                    + (m.getProximaRevisaoKm() != null ? " · próxima revisão em " + m.getProximaRevisaoKm() + " km" : "");
            itens.add(new HistoricoVeiculoItem(Tipo.MANUTENCAO, m.getId(), m.getData(), null,
                    "Manutenção · " + m.getServico().getRotulo(), detalhe.isBlank() ? null : detalhe.trim(),
                    m.getKm(), m.getValor(), m.getMotorista(), false));
        }
        for (Deslocamento d : deslocamentoRepo.findByVeiculoIdOrderByDataViagemDescIdDesc(veiculoId)) {
            String detalhe = d.getMotivo()
                    + (d.getTitular() != null ? " · " + d.getTitular() : "")
                    + (d.getKmRodados() != null ? " · " + d.getKmRodados() + " km rodados" : "")
                    + (d.getLotacao() != null ? " · " + d.getLotacao() : "");
            itens.add(new HistoricoVeiculoItem(Tipo.DESLOCAMENTO, d.getId(), d.getDataViagem(), d.getHoraSaida(),
                    "Saída · " + d.getDestino(), detalhe,
                    d.getKmFinal() != null ? d.getKmFinal() : d.getKmInicial(), null, d.getMotorista(), d.isEmAndamento()));
        }
        itens.sort(Comparator.comparing(HistoricoVeiculoItem::data).reversed()
                .thenComparing(i -> i.hora() == null ? java.time.LocalTime.MIN : i.hora(), Comparator.reverseOrder())
                .thenComparing(HistoricoVeiculoItem::id, Comparator.reverseOrder()));
        return itens;
    }

    @Transactional(readOnly = true)
    public VeiculoResumo resumo(Long veiculoId) {
        Veiculo v = carregarVeiculo(veiculoId);
        LocalDate hoje = LocalDate.now();
        LocalDate inicioAno = hoje.withDayOfMonth(1).withMonth(1);

        BigDecimal combustivel = abastecimentoRepo.totalVeiculoPeriodo(veiculoId, inicioAno, hoje);
        BigDecimal litros = abastecimentoRepo.litrosVeiculoPeriodo(veiculoId, inicioAno, hoje);
        BigDecimal manutencao = manutencaoRepo.totalVeiculoPeriodo(veiculoId, inicioAno, hoje);
        Long km = deslocamentoRepo.kmRodadosVeiculoPeriodo(veiculoId, inicioAno, hoje);
        long kmRodados = km != null ? km : 0L;
        BigDecimal consumo = (litros != null && litros.signum() > 0 && kmRodados > 0)
                ? BigDecimal.valueOf(kmRodados).divide(litros, 2, RoundingMode.HALF_UP) : null;

        DeslocamentoResponse aberto = deslocamentoRepo
                .findFirstByVeiculoIdAndKmFinalIsNullOrderByDataViagemDescIdDesc(veiculoId)
                .map(DeslocamentoResponse::from).orElse(null);

        Integer proxKm = manutencaoRepo.findFirstByVeiculoIdAndProximaRevisaoKmNotNullOrderByDataDescIdDesc(veiculoId)
                .map(ManutencaoVeiculo::getProximaRevisaoKm).orElse(null);
        LocalDate proxData = manutencaoRepo.findFirstByVeiculoIdAndProximaRevisaoDataNotNullOrderByDataDescIdDesc(veiculoId)
                .map(ManutencaoVeiculo::getProximaRevisaoData).orElse(null);
        boolean vencida = (proxKm != null && v.getKmAtual() != null && v.getKmAtual() >= proxKm)
                || (proxData != null && !proxData.isAfter(hoje));

        return new VeiculoResumo(v.getKmAtual(), nz(combustivel), nz(litros), nz(manutencao), kmRodados, consumo,
                abastecimentoRepo.countByVeiculoId(veiculoId), manutencaoRepo.countByVeiculoId(veiculoId),
                deslocamentoRepo.countByVeiculoId(veiculoId), aberto, proxKm, proxData, vencida);
    }

    @Transactional(readOnly = true)
    public FrotaResumo resumoFrota() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        return new FrotaResumo(
                veiculoRepo.countByAtivoTrue(),
                deslocamentoRepo.countByKmFinalIsNull(),
                deslocamentoRepo.countByDataViagemBetween(inicioMes, hoje),
                nz(abastecimentoRepo.totalPeriodo(inicioMes, hoje)),
                nz(manutencaoRepo.totalPeriodo(inicioMes, hoje)),
                manutencaoRepo.countByDataBetween(inicioMes, hoje),
                deslocamentoRepo.emAndamento().stream().map(DeslocamentoResponse::from).toList());
    }

    /** Nome do usuário logado, para pré-preencher o campo "motorista". */
    public static String nomeUsuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) return u.getNomeCompleto();
        return auth != null ? auth.getName() : "";
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private Veiculo carregarVeiculo(Long id) {
        return veiculoRepo.findComPatrimonio(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", id));
    }

    private VeiculoResponse toResponse(Veiculo v) {
        Optional<Deslocamento> ultima = deslocamentoRepo.findFirstByVeiculoIdOrderByDataViagemDescIdDesc(v.getId());
        boolean naRua = deslocamentoRepo.findFirstByVeiculoIdAndKmFinalIsNullOrderByDataViagemDescIdDesc(v.getId()).isPresent();
        return VeiculoResponse.from(v, naRua, ultima.map(Deslocamento::getDataViagem).orElse(null));
    }

    private PatrimonioRequest requestDePatrimonio(PatrimonioResponse atual, VeiculoRequest req, String placa) {
        return new PatrimonioRequest(
                req.numeroTombo() != null ? req.numeroTombo() : atual.numeroTombo(),
                descricaoPatrimonio(req.modelo(), placa),
                FrotaCatalog.CATEGORIA_VEICULO,
                atual.subcategoria(),
                req.dataCompra() != null ? req.dataCompra() : atual.dataCompra(),
                req.valorCompra() != null ? req.valorCompra() : atual.valorCompra(),
                req.conservacao() != null ? req.conservacao() : atual.conservacao(),
                req.notaFiscal() != null ? req.notaFiscal() : atual.notaFiscal(),
                atual.valorRecuperavel(),
                atual.conclusaoImpairment(),
                req.observacao() != null ? req.observacao() : atual.observacao(),
                atual.linkReferencia(),
                req.lotacaoId() != null ? req.lotacaoId() : atual.lotacaoId(),
                req.responsavelId() != null ? req.responsavelId() : atual.responsavelId());
    }

    static String descricaoPatrimonio(String modelo, String placa) {
        String d = ("VEÍCULO " + modelo.trim().toUpperCase() + " - PLACA " + placa);
        return d.length() > 255 ? d.substring(0, 255) : d;
    }

    private void validarPlacaUnica(String placa, Long ignorarId) {
        veiculoRepo.findByPlacaIgnoreCase(placa)
                .filter(v -> v.isAtivo() && !v.getId().equals(ignorarId))
                .ifPresent(v -> { throw new RegraDeNegocioException("Já existe um veículo ativo com a placa " + placa + "."); });
    }

    private static void validarData(LocalDate data) {
        if (data != null && data.isAfter(LocalDate.now())) {
            throw new RegraDeNegocioException("A data não pode estar no futuro.");
        }
    }

    private static String normalizarPlaca(String placa) {
        return placa.trim().toUpperCase().replaceAll("\\s+", "");
    }

    private static String loginAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : "SYSTEM";
    }

    private static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String upper(String s) {
        String t = limpar(s);
        return t == null ? null : t.toUpperCase();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

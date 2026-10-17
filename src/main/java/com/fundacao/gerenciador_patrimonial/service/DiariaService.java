package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Diaria;
import com.fundacao.gerenciador_patrimonial.domain.entity.DiariaHistorico;
import com.fundacao.gerenciador_patrimonial.domain.entity.NivelDiaria;
import com.fundacao.gerenciador_patrimonial.domain.entity.Profissional;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.domain.enums.StatusDiaria;
import com.fundacao.gerenciador_patrimonial.dto.request.DiariaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroFrota;
import com.fundacao.gerenciador_patrimonial.dto.request.NivelDiariaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.ProfissionalRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.DiariaHistoricoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.DiariaResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.NivelDiariaResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.ProfissionalResponse;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.DiariaHistoricoRepository;
import com.fundacao.gerenciador_patrimonial.repository.DiariaRepository;
import com.fundacao.gerenciador_patrimonial.repository.NivelDiariaRepository;
import com.fundacao.gerenciador_patrimonial.repository.ProfissionalRepository;
import com.fundacao.gerenciador_patrimonial.security.UsuarioAutenticado;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import java.util.List;

/**
 * Diárias de viagem (Portaria 004/2018 GAB. Pres FPTO), tabela de valores por
 * nível e cadastro de profissionais. Fluxo:
 * SOLICITADO → CONFIRMADO (admin) → DEFERIDO/INDEFERIDO (presidente) → PAGO (financeiro).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DiariaService {

    private static final String ENT = "Diaria";

    private final DiariaRepository diariaRepo;
    private final DiariaHistoricoRepository historicoRepo;
    private final ProfissionalRepository profissionalRepo;
    private final NivelDiariaRepository nivelRepo;
    private final AuditoriaService auditoriaService;

    // =========================================================================
    // Níveis (tabela de valores)
    // =========================================================================

    @Transactional(readOnly = true)
    public List<NivelDiariaResponse> listarNiveis() {
        return nivelRepo.findAllByOrderByAtivoDescIdAsc().stream().map(NivelDiariaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<NivelDiariaResponse> niveisAtivos() {
        return nivelRepo.findByAtivoTrueOrderByIdAsc().stream().map(NivelDiariaResponse::from).toList();
    }

    public NivelDiariaResponse salvarNivel(Long id, NivelDiariaRequest req) {
        NivelDiaria n = id == null ? new NivelDiaria()
                : nivelRepo.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Nível de diária", id));
        n.setDescricao(req.descricao().trim());
        n.setValorCapitais(req.valorCapitais());
        n.setValorInteriorEstado(req.valorInteriorEstado());
        n.setValorInteriorOutroEstado(req.valorInteriorOutroEstado());
        n = nivelRepo.save(n);
        auditoriaService.registrar(id == null ? AcaoAuditoria.CREATE : AcaoAuditoria.UPDATE, "NivelDiaria", n.getId(),
                "Tabela de diárias: " + n.getDescricao());
        return NivelDiariaResponse.from(n);
    }

    public void alterarAtivoNivel(Long id, boolean ativo) {
        NivelDiaria n = nivelRepo.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Nível de diária", id));
        n.setAtivo(ativo);
    }

    // =========================================================================
    // Profissionais
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ProfissionalResponse> listarProfissionais(String busca) {
        List<Profissional> lista = busca == null || busca.isBlank()
                ? profissionalRepo.listarComNivel()
                : profissionalRepo.buscar("%" + busca.trim().toLowerCase() + "%");
        return lista.stream().map(ProfissionalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProfissionalResponse> profissionaisAtivos() {
        return profissionalRepo.ativosComNivel().stream().map(ProfissionalResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProfissionalResponse buscarProfissional(Long id) {
        return ProfissionalResponse.from(carregarProfissional(id));
    }

    public ProfissionalResponse salvarProfissional(Long id, ProfissionalRequest req) {
        String cpf = formatarCpf(req.cpf());
        if (cpf == null) throw new RegraDeNegocioException("CPF deve ter 11 dígitos.");
        NivelDiaria nivel = nivelRepo.findById(req.nivelId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nível de diária", req.nivelId()));
        Profissional p = id == null ? Profissional.builder().ativo(true).cadastradoPor(nomeUsuarioAtual()).build()
                : carregarProfissional(id);
        p.setNome(req.nome().trim().toUpperCase());
        p.setMatricula(limpar(req.matricula()));
        p.setLotacao(limpar(req.lotacao()));
        p.setNivel(nivel);
        p.setCpf(cpf);
        p.setRg(limpar(req.rg()));
        p.setOrgaoExpedidor(limpar(req.orgaoExpedidor()));
        p.setDadosBancarios(limpar(req.dadosBancarios()));
        p = profissionalRepo.save(p);
        auditoriaService.registrar(id == null ? AcaoAuditoria.CREATE : AcaoAuditoria.UPDATE, "Profissional", p.getId(),
                "Profissional de diária: " + p.getNome());
        return ProfissionalResponse.from(p);
    }

    public void alterarAtivoProfissional(Long id, boolean ativo) {
        carregarProfissional(id).setAtivo(ativo);
    }

    // =========================================================================
    // Diárias
    // =========================================================================

    /** Situação que cada perfil quer ver ao abrir a tela: presidente decide as confirmadas, financeiro paga as deferidas. */
    public static StatusDiaria statusPadrao(UsuarioAutenticado logado) {
        if (logado == null) return null;
        Perfil p = logado.getUsuario().getPerfil();
        if (p == Perfil.PRESIDENTE) return StatusDiaria.CONFIRMADO;
        if (p == Perfil.FINANCEIRO) return StatusDiaria.DEFERIDO;
        return null;
    }

    @Transactional(readOnly = true)
    public Page<DiariaResponse> listar(StatusDiaria status, FiltroFrota f, Pageable pageable) {
        Specification<Diaria> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("profissional").fetch("nivel");
            }
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (f.de() != null) ps.add(cb.greaterThanOrEqualTo(root.get("partida"), f.de()));
            if (f.ate() != null) ps.add(cb.lessThanOrEqualTo(root.get("partida"), f.ate()));
            String t = f.textoOuNulo();
            if (t != null) {
                String like = "%" + t + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("itinerario")), like),
                        cb.like(cb.lower(root.get("profissional").get("nome")), like),
                        cb.like(cb.lower(root.get("solicitante")), like),
                        cb.like(cb.lower(root.get("finalidade")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return diariaRepo.findAll(spec, pageable).map(DiariaResponse::from);
    }

    @Transactional(readOnly = true)
    public long contar(StatusDiaria status) { return diariaRepo.countByStatus(status); }

    @Transactional(readOnly = true)
    public List<DiariaResponse> pendentes(StatusDiaria status, int limite) {
        return diariaRepo.pendentes(status, PageRequest.of(0, limite)).stream().map(DiariaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DiariaResponse> pagasNoPeriodo(LocalDate de, LocalDate ate) {
        return diariaRepo.pagasNoPeriodo(de, ate).stream().map(DiariaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal totalPagoNoPeriodo(LocalDate de, LocalDate ate) {
        BigDecimal v = diariaRepo.totalPagoNoPeriodo(de, ate);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Transactional(readOnly = true)
    public DiariaResponse buscar(Long id) { return DiariaResponse.from(carregar(id)); }

    @Transactional(readOnly = true)
    public List<DiariaHistoricoResponse> historico(Long id) {
        return historicoRepo.findByDiariaIdOrderByInstanteAscIdAsc(id).stream().map(DiariaHistoricoResponse::from).toList();
    }

    public DiariaResponse criar(DiariaRequest req) {
        validar(req);
        Profissional p = carregarProfissional(req.profissionalId());
        Diaria d = Diaria.builder().status(StatusDiaria.SOLICITADO).solicitante(nomeUsuarioAtual())
                .dataSolicitacao(LocalDate.now()).build();
        aplicar(d, req, p);
        d = diariaRepo.save(d);
        registrar(d, null, StatusDiaria.SOLICITADO, "Solicitação aberta.");
        auditoriaService.registrar(AcaoAuditoria.CREATE, ENT, d.getId(),
                "Diária solicitada para %s: %s, R$ %s".formatted(p.getNome(), d.getItinerario(), d.getValorTotal()));
        return DiariaResponse.from(d);
    }

    public DiariaResponse atualizar(Long id, DiariaRequest req) {
        validar(req);
        Diaria d = carregar(id);
        if (!d.isEditavel()) throw new RegraDeNegocioException("Diária já paga não pode ser alterada.");
        aplicar(d, req, carregarProfissional(req.profissionalId()));
        registrar(d, d.getStatus(), d.getStatus(), "Dados da solicitação alterados.");
        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT, id, "Diária alterada");
        return DiariaResponse.from(d);
    }

    /** Única porta de mudança de situação. Valida a transição e grava o histórico com quem fez. */
    public DiariaResponse transicionar(Long id, StatusDiaria destino, String observacao, LocalDate dataPagamento) {
        Diaria d = carregar(id);
        StatusDiaria origem = d.getStatus();
        if (!origem.podeIr(destino)) {
            throw new RegraDeNegocioException("Diária " + origem.getRotulo().toLowerCase()
                    + " não pode passar para " + destino.getRotulo().toLowerCase() + ".");
        }
        if (destino == StatusDiaria.INDEFERIDO && (observacao == null || observacao.isBlank())) {
            throw new RegraDeNegocioException("Informe o motivo do indeferimento.");
        }
        if (destino == StatusDiaria.PAGO) {
            LocalDate dt = dataPagamento == null ? LocalDate.now() : dataPagamento;
            if (dt.isAfter(LocalDate.now())) throw new RegraDeNegocioException("A data de pagamento não pode estar no futuro.");
            d.setDataPagamento(dt);
        }
        if (destino == StatusDiaria.INDEFERIDO) d.setMotivoIndeferimento(observacao.trim());
        d.setStatus(destino);
        registrar(d, origem, destino, observacao);
        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT, id,
                "Diária %s → %s".formatted(origem.getRotulo(), destino.getRotulo())
                        + (observacao != null && !observacao.isBlank() ? ": " + observacao.trim() : ""));
        return DiariaResponse.from(d);
    }

    public void excluir(Long id) {
        Diaria d = carregar(id);
        if (!d.isEditavel()) throw new RegraDeNegocioException("Diária já paga não pode ser excluída.");
        historicoRepo.deleteByDiariaId(id);
        diariaRepo.delete(d);
        auditoriaService.registrar(AcaoAuditoria.DELETE, ENT, id, "Diária excluída");
    }

    /** Valor sugerido para o formulário a partir da tabela do profissional. */
    @Transactional(readOnly = true)
    public ProfissionalResponse valoresDoProfissional(Long profissionalId) {
        return ProfissionalResponse.from(carregarProfissional(profissionalId));
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private Diaria carregar(Long id) {
        return diariaRepo.findComProfissional(id).orElseThrow(() -> new RecursoNaoEncontradoException("Diária", id));
    }

    private Profissional carregarProfissional(Long id) {
        return profissionalRepo.findComNivel(id).orElseThrow(() -> new RecursoNaoEncontradoException("Profissional", id));
    }

    private static void validar(DiariaRequest req) {
        if (req.partida() != null && req.retorno() != null && req.retorno().isBefore(req.partida())) {
            throw new RegraDeNegocioException("O retorno não pode ser antes da partida.");
        }
    }

    private static void aplicar(Diaria d, DiariaRequest req, Profissional p) {
        d.setProfissional(p);
        d.setItinerario(req.itinerario().trim());
        d.setHoraSaida(req.horaSaida());
        d.setMeioTransporte(req.meioTransporte());
        d.setPartida(req.partida());
        d.setRetorno(req.retorno());
        d.setHoraChegada(req.horaChegada());
        d.setRecurso(req.recurso());
        d.setNumeroDiarias(req.numeroDiarias());
        d.setValorUnitario(req.valorUnitario());
        d.setValorTotal(req.numeroDiarias().multiply(req.valorUnitario()).setScale(2, RoundingMode.HALF_UP));
        d.setFinalidade(limpar(req.finalidade()));
    }

    private void registrar(Diaria d, StatusDiaria de, StatusDiaria para, String observacao) {
        historicoRepo.save(DiariaHistorico.builder().diaria(d).deStatus(de).paraStatus(para)
                .usuario(nomeUsuarioAtual()).observacao(limpar(observacao)).build());
    }

    /** Formata para 000.000.000-00; null se não tiver 11 dígitos. */
    public static String formatarCpf(String cpf) {
        if (cpf == null) return null;
        String d = cpf.replaceAll("\\D", "");
        if (d.length() != 11) return null;
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    private static String nomeUsuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) return u.getNomeCompleto();
        return auth != null && auth.isAuthenticated() ? auth.getName() : "SYSTEM";
    }

    private static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

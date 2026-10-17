package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Cautela;
import com.fundacao.gerenciador_patrimonial.domain.entity.Veiculo;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.dto.request.CautelaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.FiltroFrota;
import com.fundacao.gerenciador_patrimonial.dto.request.FinalizarDeslocamentoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.CautelaResponse;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.CautelaRepository;
import com.fundacao.gerenciador_patrimonial.repository.VeiculoRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Cautelas: entrega de veículo a condutor externo com termo de responsabilidade. */
@Service
@RequiredArgsConstructor
@Transactional
public class CautelaService {

    private final CautelaRepository cautelaRepo;
    private final VeiculoRepository veiculoRepo;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public Page<CautelaResponse> listar(FiltroFrota f, Pageable pageable) {
        Specification<Cautela> spec = (root, query, cb) -> {
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
                        cb.like(cb.lower(root.get("motoristaNome")), like),
                        cb.like(cb.lower(root.get("lotacao")), like),
                        cb.like(cb.lower(root.get("veiculo").get("placa")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return cautelaRepo.findAll(spec, pageable).map(CautelaResponse::from);
    }

    @Transactional(readOnly = true)
    public List<CautelaResponse> emAberto() {
        return cautelaRepo.emAberto().stream().map(CautelaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CautelaResponse> doVeiculo(Long veiculoId) {
        return cautelaRepo.findByVeiculoIdOrderByDataViagemDescIdDesc(veiculoId).stream().map(CautelaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CautelaResponse buscar(Long id) { return CautelaResponse.from(carregar(id)); }

    public CautelaResponse criar(CautelaRequest req) {
        validar(req);
        Veiculo v = veiculoRepo.findById(req.veiculoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", req.veiculoId()));
        if (req.kmFinal() == null && cautelaRepo.findFirstByVeiculoIdAndKmFinalIsNullOrderByDataViagemDescIdDesc(v.getId()).isPresent()) {
            throw new RegraDeNegocioException("Este veículo já tem uma cautela em aberto. Finalize-a antes.");
        }
        Cautela c = Cautela.builder().veiculo(v).registradoPor(loginAtual()).build();
        aplicar(c, req);
        c = cautelaRepo.save(c);
        v.atualizarKm(c.getKmInicial());
        v.atualizarKm(c.getKmFinal());
        auditoriaService.registrar(AcaoAuditoria.FROTA, "Veiculo", v.getId(),
                "Cautela #%d: %s para %s (%s)".formatted(c.getId(), v.getPlaca(), c.getMotoristaNome(), c.getDestino()));
        return CautelaResponse.from(c);
    }

    public CautelaResponse atualizar(Long id, CautelaRequest req) {
        validar(req);
        Cautela c = carregar(id);
        if (req.veiculoId() != null && !req.veiculoId().equals(c.getVeiculo().getId())) {
            c.setVeiculo(veiculoRepo.findById(req.veiculoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", req.veiculoId())));
        }
        aplicar(c, req);
        c.getVeiculo().atualizarKm(c.getKmInicial());
        c.getVeiculo().atualizarKm(c.getKmFinal());
        auditoriaService.registrar(AcaoAuditoria.FROTA, "Veiculo", c.getVeiculo().getId(), "Cautela #%d alterada".formatted(id));
        return CautelaResponse.from(c);
    }

    public CautelaResponse finalizar(Long id, FinalizarDeslocamentoRequest req) {
        Cautela c = carregar(id);
        if (!c.isEmAndamento()) throw new RegraDeNegocioException("Esta cautela já foi finalizada.");
        if (c.getKmInicial() != null && req.kmFinal() < c.getKmInicial()) {
            throw new RegraDeNegocioException("Km final não pode ser menor que o km inicial (" + c.getKmInicial() + ").");
        }
        c.setKmFinal(req.kmFinal());
        c.setHoraChegada(req.horaChegada());
        c.getVeiculo().atualizarKm(req.kmFinal());
        auditoriaService.registrar(AcaoAuditoria.FROTA, "Veiculo", c.getVeiculo().getId(),
                "Cautela #%d finalizada: km final %d".formatted(id, req.kmFinal()));
        return CautelaResponse.from(c);
    }

    public void excluir(Long id) {
        Cautela c = carregar(id);
        Long veiculoId = c.getVeiculo().getId();
        cautelaRepo.delete(c);
        auditoriaService.registrar(AcaoAuditoria.FROTA, "Veiculo", veiculoId, "Cautela #%d excluída".formatted(id));
    }

    private Cautela carregar(Long id) {
        return cautelaRepo.findComVeiculo(id).orElseThrow(() -> new RecursoNaoEncontradoException("Cautela", id));
    }

    private static void validar(CautelaRequest req) {
        if (req.veiculoId() == null) throw new RegraDeNegocioException("Selecione o veículo.");
        if (req.kmInicial() != null && req.kmFinal() != null && req.kmFinal() < req.kmInicial()) {
            throw new RegraDeNegocioException("Km final não pode ser menor que o km inicial.");
        }
        if (req.dataViagem() != null && req.dataViagem().isAfter(LocalDate.now().plusDays(1))) {
            throw new RegraDeNegocioException("A data não pode estar no futuro.");
        }
        String cpf = req.cpf() == null ? "" : req.cpf().replaceAll("\\D", "");
        if (cpf.length() != 11) throw new RegraDeNegocioException("CPF do condutor deve ter 11 dígitos.");
    }

    private static void aplicar(Cautela c, CautelaRequest req) {
        c.setDataViagem(req.dataViagem());
        c.setDestino(req.destino().trim());
        c.setMotivo(req.motivo().trim());
        c.setResponsavel(limpar(req.responsavel()));
        c.setAssistido(limpar(req.assistido()));
        c.setHoraSaida(req.horaSaida());
        c.setKmInicial(req.kmInicial());
        c.setHoraChegada(req.horaChegada());
        c.setKmFinal(req.kmFinal());
        c.setMotoristaNome(req.motoristaNome().trim().toUpperCase());
        c.setRg(req.rg().trim());
        c.setCpf(DiariaService.formatarCpf(req.cpf()));
        c.setFone(req.fone().trim());
        c.setLotacao(req.lotacao().trim());
        c.setObservacao(limpar(req.observacao()));
    }

    private static String loginAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : "SYSTEM";
    }

    private static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

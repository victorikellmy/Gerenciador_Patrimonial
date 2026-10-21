package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.SeguroVeiculo;
import com.fundacao.gerenciador_patrimonial.domain.entity.SinistroVeiculo;
import com.fundacao.gerenciador_patrimonial.domain.entity.Veiculo;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.domain.enums.SituacaoSinistro;
import com.fundacao.gerenciador_patrimonial.dto.request.SeguroVeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.SinistroVeiculoRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.AlertasSeguroResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.SeguroVeiculoResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.SinistroVeiculoResponse;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.SeguroVeiculoRepository;
import com.fundacao.gerenciador_patrimonial.repository.SinistroVeiculoRepository;
import com.fundacao.gerenciador_patrimonial.repository.VeiculoRepository;
import com.fundacao.gerenciador_patrimonial.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Seguro dos veículos da frota: apólices (com arquivo), sinistros e alertas de vencimento. */
@Service
@RequiredArgsConstructor
@Transactional
public class SeguroVeiculoService {

    private static final String ENT_VEICULO = "Veiculo";

    private final SeguroVeiculoRepository seguroRepo;
    private final SinistroVeiculoRepository sinistroRepo;
    private final VeiculoRepository veiculoRepo;
    private final StorageService storageService;
    private final AuditoriaService auditoriaService;

    // =========================================================================
    // Apólices
    // =========================================================================

    /**
     * Lista as apólices, mais recentes primeiro. {@code situacao} filtra pela situação de hoje
     * (VIGENTE, VENCENDO, VENCIDO, FUTURO); {@code texto} procura em placa, modelo, seguradora e apólice.
     */
    @Transactional(readOnly = true)
    public List<SeguroVeiculoResponse> listar(Long veiculoId, SeguroVeiculo.Situacao situacao, String texto) {
        LocalDate hoje = LocalDate.now();
        String t = texto == null || texto.isBlank() ? null : texto.trim().toLowerCase(Locale.ROOT);
        List<SeguroVeiculo> base = veiculoId != null ? seguroRepo.doVeiculo(veiculoId) : seguroRepo.todos();
        return base.stream()
                .filter(s -> situacao == null || s.situacao(hoje) == situacao)
                .filter(s -> t == null || contem(t, s.getVeiculo().getPlaca(), s.getVeiculo().getModelo(),
                        s.getSeguradora(), s.getNumeroApolice(), s.getCorretor()))
                .map(SeguroVeiculoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SeguroVeiculoResponse buscar(Long id) { return SeguroVeiculoResponse.from(carregar(id)); }

    public SeguroVeiculoResponse criar(SeguroVeiculoRequest req, MultipartFile apolice) {
        validar(req);
        if (apolice != null && !apolice.isEmpty()) validarArquivoApolice(apolice);
        Veiculo v = veiculo(req.veiculoId());
        SeguroVeiculo s = SeguroVeiculo.builder().veiculo(v).registradoPor(loginAtual()).build();
        aplicar(s, req);
        s = seguroRepo.save(s);
        if (apolice != null && !apolice.isEmpty()) gravarApolice(s, apolice);
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, v.getId(),
                "Seguro #%d: %s apólice %s, vigência %s a %s".formatted(s.getId(), s.getSeguradora(),
                        s.getNumeroApolice(), s.getInicioVigencia(), s.getFimVigencia()));
        return SeguroVeiculoResponse.from(s);
    }

    public SeguroVeiculoResponse atualizar(Long id, SeguroVeiculoRequest req) {
        validar(req);
        SeguroVeiculo s = carregar(id);
        if (!s.getVeiculo().getId().equals(req.veiculoId())) s.setVeiculo(veiculo(req.veiculoId()));
        aplicar(s, req);
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, s.getVeiculo().getId(),
                "Seguro #%d alterado (%s, até %s)".formatted(id, s.getNumeroApolice(), s.getFimVigencia()));
        return SeguroVeiculoResponse.from(s);
    }

    /** Anexa ou substitui o arquivo da apólice; o anterior fica no armazenamento e na auditoria. */
    public SeguroVeiculoResponse anexarApolice(Long id, MultipartFile arquivo) {
        validarArquivoApolice(arquivo);
        SeguroVeiculo s = carregar(id);
        gravarApolice(s, arquivo);
        return SeguroVeiculoResponse.from(s);
    }

    @Transactional(readOnly = true)
    public FrotaService.NotaFiscalArquivo apolice(Long id) {
        SeguroVeiculo s = seguroRepo.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Seguro", id));
        if (!s.possuiApolice()) throw new RecursoNaoEncontradoException("Arquivo da apólice do seguro", id);
        return new FrotaService.NotaFiscalArquivo(s.getApoliceNomeOriginal(), s.getApoliceContentType(),
                storageService.carregar(s.getApoliceCaminho()));
    }

    public void excluir(Long id) {
        SeguroVeiculo s = carregar(id);
        if (sinistroRepo.existsBySeguroId(id)) {
            throw new RegraDeNegocioException("Esta apólice tem sinistros registrados. Exclua ou altere os sinistros antes.");
        }
        Long veiculoId = s.getVeiculo().getId();
        seguroRepo.delete(s);
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, veiculoId,
                "Seguro #%d excluído (%s)".formatted(id, s.getNumeroApolice()));
    }

    // =========================================================================
    // Sinistros
    // =========================================================================

    @Transactional(readOnly = true)
    public List<SinistroVeiculoResponse> sinistros(Long veiculoId) {
        return (veiculoId != null ? sinistroRepo.doVeiculo(veiculoId) : sinistroRepo.todos())
                .stream().map(SinistroVeiculoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SinistroVeiculoResponse buscarSinistro(Long id) { return SinistroVeiculoResponse.from(carregarSinistro(id)); }

    public SinistroVeiculoResponse criarSinistro(SinistroVeiculoRequest req) {
        validar(req);
        Veiculo v = veiculo(req.veiculoId());
        SinistroVeiculo s = SinistroVeiculo.builder().veiculo(v).registradoPor(loginAtual()).build();
        aplicar(s, req);
        s = sinistroRepo.save(s);
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, v.getId(),
                "Sinistro #%d em %s: %s".formatted(s.getId(), s.getDataOcorrencia(), resumir(s.getDescricao())));
        return SinistroVeiculoResponse.from(s);
    }

    public SinistroVeiculoResponse atualizarSinistro(Long id, SinistroVeiculoRequest req) {
        validar(req);
        SinistroVeiculo s = carregarSinistro(id);
        if (!s.getVeiculo().getId().equals(req.veiculoId())) s.setVeiculo(veiculo(req.veiculoId()));
        SituacaoSinistro antes = s.getSituacao();
        aplicar(s, req);
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, s.getVeiculo().getId(),
                antes == s.getSituacao() ? "Sinistro #%d alterado".formatted(id)
                        : "Sinistro #%d: %s → %s".formatted(id, antes.getRotulo(), s.getSituacao().getRotulo()));
        return SinistroVeiculoResponse.from(s);
    }

    public void excluirSinistro(Long id) {
        SinistroVeiculo s = carregarSinistro(id);
        Long veiculoId = s.getVeiculo().getId();
        sinistroRepo.delete(s);
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, veiculoId, "Sinistro #%d excluído".formatted(id));
    }

    // =========================================================================
    // Alertas (painel de gestão)
    // =========================================================================

    /**
     * Situação do seguro da frota ativa. Para cada veículo vale a apólice mais nova: se ela já
     * venceu (e não há renovação), o veículo entra em "vencidos"; se vence em até 30 dias, em
     * "vencendo"; veículo sem nenhuma apólice entra em "sem seguro".
     */
    @Transactional(readOnly = true)
    public AlertasSeguroResponse alertas() {
        LocalDate hoje = LocalDate.now();
        Map<Long, List<SeguroVeiculo>> porVeiculo = seguroRepo.todos().stream()
                .collect(Collectors.groupingBy(s -> s.getVeiculo().getId()));
        List<SeguroVeiculoResponse> vencidos = new java.util.ArrayList<>();
        List<SeguroVeiculoResponse> vencendo = new java.util.ArrayList<>();
        List<AlertasSeguroResponse.VeiculoSemSeguro> semSeguro = new java.util.ArrayList<>();
        for (Veiculo v : veiculoRepo.findByAtivoTrueOrderByPlacaAsc()) {
            List<SeguroVeiculo> apolices = porVeiculo.getOrDefault(v.getId(), List.of());
            Optional<SeguroVeiculo> atual = apolices.stream().filter(s -> s.cobre(hoje))
                    .max(Comparator.comparing(SeguroVeiculo::getFimVigencia));
            if (atual.isPresent()) {
                SeguroVeiculo s = atual.get();
                // Vencendo, mas já com a renovação cadastrada (começa depois): não alerta.
                boolean renovado = apolices.stream().anyMatch(o -> o.getInicioVigencia().isAfter(hoje));
                if (s.situacao(hoje) == SeguroVeiculo.Situacao.VENCENDO && !renovado) vencendo.add(SeguroVeiculoResponse.from(s));
                continue;
            }
            Optional<SeguroVeiculo> ultima = apolices.stream().max(Comparator.comparing(SeguroVeiculo::getFimVigencia));
            if (ultima.isEmpty()) semSeguro.add(new AlertasSeguroResponse.VeiculoSemSeguro(v.getId(), v.getPlaca(), v.getModelo()));
            else if (ultima.get().situacao(hoje) == SeguroVeiculo.Situacao.VENCIDO) vencidos.add(SeguroVeiculoResponse.from(ultima.get()));
            // Só apólice futura (ainda não começou): o veículo está sem cobertura hoje.
            else semSeguro.add(new AlertasSeguroResponse.VeiculoSemSeguro(v.getId(), v.getPlaca(), v.getModelo()));
        }
        vencendo.sort(Comparator.comparing(SeguroVeiculoResponse::fimVigencia));
        vencidos.sort(Comparator.comparing(SeguroVeiculoResponse::fimVigencia));
        List<SinistroVeiculoResponse> abertos = sinistroRepo.todos().stream()
                .filter(s -> s.getSituacao().emAndamento()).map(SinistroVeiculoResponse::from).toList();
        return new AlertasSeguroResponse(SeguroVeiculo.DIAS_AVISO_VENCIMENTO, vencidos, vencendo, semSeguro, abertos);
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private void gravarApolice(SeguroVeiculo s, MultipartFile arquivo) {
        String anterior = s.possuiApolice() ? s.getApoliceNomeOriginal() + " em " + s.getApoliceCaminho() : null;
        String caminho = storageService.armazenar(arquivo, "frota/" + s.getVeiculo().getId() + "/seguros");
        s.setApoliceCaminho(caminho);
        s.setApoliceNomeOriginal(arquivo.getOriginalFilename());
        s.setApoliceContentType(arquivo.getContentType());
        s.setApoliceTamanhoBytes(arquivo.getSize());
        auditoriaService.registrar(AcaoAuditoria.FROTA, ENT_VEICULO, s.getVeiculo().getId(),
                (anterior == null ? "Apólice anexada ao seguro #%d (%s)" : "Apólice substituída no seguro #%d (%s; anterior mantida: " + anterior + ")")
                        .formatted(s.getId(), arquivo.getOriginalFilename()));
    }

    private static final java.util.Set<String> TIPOS_APOLICE = java.util.Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");

    private static void validarArquivoApolice(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) throw new RegraDeNegocioException("Arquivo da apólice vazio.");
        if (arquivo.getContentType() == null || !TIPOS_APOLICE.contains(arquivo.getContentType())) {
            throw new RegraDeNegocioException("A apólice deve ser PDF ou foto (JPEG, PNG, WEBP).");
        }
    }

    private Veiculo veiculo(Long id) {
        return veiculoRepo.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Veículo", id));
    }

    private SeguroVeiculo carregar(Long id) {
        return seguroRepo.findComVeiculo(id).orElseThrow(() -> new RecursoNaoEncontradoException("Seguro", id));
    }

    private SinistroVeiculo carregarSinistro(Long id) {
        return sinistroRepo.findComVeiculo(id).orElseThrow(() -> new RecursoNaoEncontradoException("Sinistro", id));
    }

    private static void validar(SeguroVeiculoRequest req) {
        if (req.fimVigencia().isBefore(req.inicioVigencia())) {
            throw new RegraDeNegocioException("O fim da vigência não pode ser antes do início.");
        }
    }

    private void validar(SinistroVeiculoRequest req) {
        if (req.dataOcorrencia().isAfter(LocalDate.now())) {
            throw new RegraDeNegocioException("A data da ocorrência não pode estar no futuro.");
        }
        if (req.seguroId() != null) {
            SeguroVeiculo seg = carregar(req.seguroId());
            if (!seg.getVeiculo().getId().equals(req.veiculoId())) {
                throw new RegraDeNegocioException("A apólice escolhida é de outro veículo.");
            }
        }
    }

    private static void aplicar(SeguroVeiculo s, SeguroVeiculoRequest req) {
        s.setSeguradora(req.seguradora().trim().toUpperCase(Locale.ROOT));
        s.setNumeroApolice(req.numeroApolice().trim());
        s.setCorretor(limpar(req.corretor()));
        s.setInicioVigencia(req.inicioVigencia());
        s.setFimVigencia(req.fimVigencia());
        s.setValorPremio(req.valorPremio());
        s.setFranquia(req.franquia());
        s.setCoberturas(limpar(req.coberturas()));
        s.setTelefoneAssistencia(limpar(req.telefoneAssistencia()));
        s.setObservacao(limpar(req.observacao()));
    }

    private void aplicar(SinistroVeiculo s, SinistroVeiculoRequest req) {
        // Sem apólice informada: usa a que cobria o veículo na data da ocorrência.
        SeguroVeiculo seguro = req.seguroId() != null ? carregar(req.seguroId())
                : seguroRepo.doVeiculo(req.veiculoId()).stream().filter(x -> x.cobre(req.dataOcorrencia())).findFirst().orElse(null);
        s.setSeguro(seguro);
        s.setDataOcorrencia(req.dataOcorrencia());
        s.setNumeroSinistro(limpar(req.numeroSinistro()));
        s.setDescricao(req.descricao().trim());
        s.setLocalOcorrencia(limpar(req.localOcorrencia()));
        s.setCondutor(limpar(req.condutor()));
        s.setValorPrejuizo(req.valorPrejuizo());
        s.setValorIndenizado(req.valorIndenizado());
        s.setSituacao(req.situacao() != null ? req.situacao() : SituacaoSinistro.ABERTO);
        s.setObservacao(limpar(req.observacao()));
    }

    private static boolean contem(String termo, String... campos) {
        for (String c : campos) if (c != null && c.toLowerCase(Locale.ROOT).contains(termo)) return true;
        return false;
    }

    private static String resumir(String s) { return s.length() <= 80 ? s : s.substring(0, 77) + "..."; }

    private static String loginAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : "SYSTEM";
    }

    private static String limpar(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}

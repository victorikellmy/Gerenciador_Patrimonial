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
import jakarta.persistence.criteria.JoinType;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Diárias de viagem: requerimento do Anexo II da Portaria 001/2019 GAB. PRES, tabela de valores
 * por nível e cadastro de profissionais. Fluxo:
 * SOLICITADO → CONFIRMADO (admin) → DEFERIDO/INDEFERIDO (presidente) → PAGO (financeiro).
 *
 * <p>Regras do requerimento: todos os campos do pedido são obrigatórios; a quantidade de diárias
 * é apurada pelo servidor ({@link CalculoDiarias}); os dados do profissional informados no pedido
 * são gravados no cadastro (criando-o se for a primeira vez) e copiados para a diária.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DiariaService {

    private static final String ENT = "Diaria";

    /** Parecer registrado quando o presidente autoriza sem escrever observação (texto do Anexo II). */
    public static final String FUNDAMENTACAO_PADRAO = "AUTORIZADO, com base nas atribuições constantes no disposto no "
            + "artigo 1° e 4°, inciso I, da Portaria 001/2019 - Gab. Pres. da Fundação Pró-Tocantins.";

    // Formatos do formulário de referência (Formulario_Diarias_FPTO.html).
    private static final Pattern SEM_NUMEROS = Pattern.compile("^[\\p{L}\\p{M} .,'’\\-–/()]+$");
    private static final Pattern NUMERO_REQUERIMENTO = Pattern.compile("^(?=[0-9/]*[0-9])[0-9/]+$");
    private static final Pattern IDENTIFICADOR = Pattern.compile("^(?=[0-9/\\-]*[0-9])[0-9/\\-]+$");
    private static final Pattern BANCO = Pattern.compile("^[0-9]{3}$");
    private static final Pattern AGENCIA = Pattern.compile("^[0-9]+(?:-[0-9]+)?$");
    private static final Pattern CONTA = Pattern.compile("^[0-9]+(?:-(?:[0-9]+|[Xx]))?$");
    private static final Pattern PIX = Pattern.compile("^\\S+$");

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

    /**
     * Busca do formulário de diária: até 10 profissionais ativos por nome, matrícula ou CPF
     * (com ou sem pontuação). Menos de 2 caracteres não busca.
     */
    @Transactional(readOnly = true)
    public List<ProfissionalResponse> buscarParaRequerimento(String termo) {
        if (termo == null || termo.trim().length() < 2) return List.of();
        String t = termo.trim().toLowerCase();
        String digitos = t.replaceAll("\\D", "");
        // "#" nunca aparece num CPF só com algarismos: desliga a comparação por dígitos.
        String porDigitos = digitos.length() >= 3 ? "%" + digitos + "%" : "#";
        return profissionalRepo.buscarParaFormulario("%" + t + "%", porDigitos, PageRequest.of(0, 10))
                .stream().map(ProfissionalResponse::from).toList();
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
        NivelDiaria nivel = req.nivelId() == null ? null : nivelRepo.findById(req.nivelId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nível de diária", req.nivelId()));
        Profissional p = id == null ? Profissional.builder().ativo(true).cadastradoPor(nomeUsuarioAtual()).build()
                : carregarProfissional(id);
        profissionalRepo.findFirstByCpf(cpf).filter(outro -> !outro.getId().equals(p.getId())).ifPresent(outro -> {
            throw new RegraDeNegocioException("O CPF informado já está cadastrado para " + outro.getNome() + ".");
        });
        validarBancarios(req.banco(), req.agencia(), req.contaCorrente(), req.pix(), false);
        p.setNome(req.nome().trim().toUpperCase());
        p.setMatricula(limpar(req.matricula()));
        p.setLotacao(limpar(req.lotacao()));
        p.setNivel(nivel);
        p.setCpf(cpf);
        p.setRg(limpar(req.rg()));
        p.setOrgaoExpedidor(limpar(req.orgaoExpedidor()));
        p.setDadosBancarios(limpar(req.dadosBancarios()));
        // Campos novos: null = cliente antigo que não os conhece (mantém); "" = apagar.
        if (req.cargo() != null) p.setCargo(limpar(req.cargo()));
        if (req.banco() != null) p.setBanco(limpar(req.banco()));
        if (req.agencia() != null) p.setAgencia(limpar(req.agencia()));
        if (req.contaCorrente() != null) p.setContaCorrente(limpar(req.contaCorrente()));
        if (req.pix() != null) p.setPix(limpar(req.pix()));
        Profissional salvo = profissionalRepo.save(p);
        auditoriaService.registrar(id == null ? AcaoAuditoria.CREATE : AcaoAuditoria.UPDATE, "Profissional", salvo.getId(),
                "Profissional de diária: " + salvo.getNome());
        return ProfissionalResponse.from(salvo);
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
                root.fetch("profissional").fetch("nivel", JoinType.LEFT);
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
                        cb.like(cb.lower(root.get("profNome")), like),
                        cb.like(cb.lower(root.get("numeroRequerimento")), like),
                        cb.like(cb.lower(root.get("origem")), like),
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
        Profissional p = registrarProfissional(req);
        LocalDate dataRequerimento = req.dataRequerimento() != null ? req.dataRequerimento() : LocalDate.now();
        Diaria d = Diaria.builder().status(StatusDiaria.SOLICITADO).solicitante(nomeUsuarioAtual())
                .dataSolicitacao(dataRequerimento).build();
        d.setNumeroRequerimento(numeroPara(d, req, dataRequerimento));
        aplicar(d, req, p);
        d = diariaRepo.save(d);
        registrar(d, null, StatusDiaria.SOLICITADO, "Requerimento nº " + d.getNumeroRequerimento() + " aberto.");
        auditoriaService.registrar(AcaoAuditoria.CREATE, ENT, d.getId(),
                "Diária nº %s solicitada para %s: %s, R$ %s".formatted(d.getNumeroRequerimento(), p.getNome(),
                        d.getItinerario(), d.getValorTotal()));
        return DiariaResponse.from(d);
    }

    public DiariaResponse atualizar(Long id, DiariaRequest req) {
        validar(req);
        Diaria d = carregar(id);
        if (!d.isEditavel()) throw new RegraDeNegocioException("Diária já paga não pode ser alterada.");
        StatusDiaria anterior = d.getStatus();
        String antes = resumo(d);
        Profissional p = registrarProfissional(req);
        if (req.dataRequerimento() != null) d.setDataSolicitacao(req.dataRequerimento());
        d.setNumeroRequerimento(numeroPara(d, req, d.getDataSolicitacao()));
        aplicar(d, req, p);
        String depois = resumo(d);
        // Separação de funções: o que foi confirmado/deferido vale para os dados daquele momento.
        // Qualquer alteração depois disso devolve a diária para "Solicitada" (nova confirmação e
        // novo deferimento), para que o Financeiro nunca pague valor ou beneficiário não aprovado.
        String obs;
        if (anterior != StatusDiaria.SOLICITADO) {
            d.setStatus(StatusDiaria.SOLICITADO);
            d.setMotivoIndeferimento(null);
            obs = "Alterada após " + anterior.getRotulo().toLowerCase() + "; volta para nova aprovação. ";
        } else {
            obs = "Dados alterados. ";
        }
        obs += "Antes: " + antes + " | Depois: " + depois;
        registrar(d, anterior, d.getStatus(), truncar(obs, 500));
        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT, id, "Diária alterada. Antes: " + antes + " | Depois: " + depois);
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
        if (destino == StatusDiaria.DEFERIDO && (observacao == null || observacao.isBlank())) observacao = FUNDAMENTACAO_PADRAO;
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

    /** Confere obrigatoriedade e formato, na ordem das seções do requerimento. */
    private static void validar(DiariaRequest req) {
        if (req.formatoAntigo()) {
            throw new RegraDeNegocioException("Esta versão do aplicativo não envia os dados do novo Requerimento de Diárias "
                    + "(tipo de diária, origem, horários e dados bancários). Abra a solicitação pelo portal ou atualize o aplicativo.");
        }
        // 1. Identificação
        if (temTexto(req.numeroRequerimento()) && !NUMERO_REQUERIMENTO.matcher(req.numeroRequerimento().trim()).matches()) {
            throw new RegraDeNegocioException("Número do requerimento: use somente algarismos e barra (/).");
        }
        exigir(req.matricula(), "a matrícula funcional");
        formato(IDENTIFICADOR, req.matricula(), "Matrícula funcional: use somente algarismos, barra (/) e hífen (-).");
        exigir(req.nome(), "o nome do profissional");
        formato(SEM_NUMEROS, req.nome(), "Profissional: use letras e sinais de pontuação, sem números.");
        exigir(req.cargo(), "o cargo, posto ou função");
        exigir(req.lotacao(), "a lotação");
        exigir(req.cpf(), "o CPF");
        exigir(req.orgaoExpedidor(), "o órgão expedidor");
        formato(SEM_NUMEROS, req.orgaoExpedidor(), "Órgão expedidor: use letras e sinais de pontuação, sem números.");
        // 2. Missão e deslocamento
        exigir(req.origem(), "a origem");
        formato(SEM_NUMEROS, req.origem(), "Origem: use letras e sinais de pontuação, sem números.");
        exigir(req.itinerario(), "o destino / itinerário");
        if (req.partida() == null) throw new RegraDeNegocioException("Informe a data de saída.");
        if (req.horaSaida() == null) throw new RegraDeNegocioException("Informe o horário de saída.");
        if (req.retorno() == null) throw new RegraDeNegocioException("Informe a data de retorno à origem.");
        if (req.horaChegada() == null) throw new RegraDeNegocioException("Informe o horário de retorno.");
        if (req.meioTransporte() == null) throw new RegraDeNegocioException("Selecione o meio de transporte.");
        if (req.recurso() == null) throw new RegraDeNegocioException("Selecione a fonte do recurso.");
        exigir(req.finalidade(), "a finalidade da viagem");
        validarBancarios(req.banco(), req.agencia(), req.contaCorrente(), req.pix(), true);
        // 3. Apuração
        if (req.tipoDiaria() == null) throw new RegraDeNegocioException("Selecione o tipo de diária.");
        if (req.valorUnitario() == null || req.valorUnitario().signum() <= 0) {
            throw new RegraDeNegocioException("Informe o valor unitário da diária integral para o tipo selecionado.");
        }
    }

    private static void validarBancarios(String banco, String agencia, String conta, String pix, boolean obrigatorio) {
        if (obrigatorio) {
            exigir(banco, "o código do banco");
            exigir(agencia, "a agência");
            exigir(conta, "a conta corrente");
            exigir(pix, "a chave PIX");
        }
        formato(BANCO, banco, "Banco: informe o código com exatamente três dígitos.");
        formato(AGENCIA, agencia, "Agência: use algarismos e, se necessário, hífen e dígito.");
        formato(CONTA, conta, "Conta corrente: use algarismos e, se for o dígito verificador, X após o hífen.");
        formato(PIX, pix, "PIX: informe a chave sem espaços.");
    }

    private static boolean temTexto(String s) { return s != null && !s.isBlank(); }

    private static void exigir(String valor, String oQue) {
        if (!temTexto(valor)) throw new RegraDeNegocioException("Informe " + oQue + ".");
    }

    /** Só confere quando há valor (a obrigatoriedade é tratada à parte). */
    private static void formato(Pattern padrao, String valor, String mensagem) {
        if (temTexto(valor) && !padrao.matcher(valor.trim()).matches()) throw new RegraDeNegocioException(mensagem);
    }

    /**
     * "Conforme vai solicitando, vai guardando": localiza o profissional pelo id escolhido na busca
     * ou pelo CPF, cria o cadastro se for a primeira diária dele e grava o que veio no requerimento,
     * para a próxima solicitação já trazer tudo preenchido.
     */
    private Profissional registrarProfissional(DiariaRequest req) {
        String cpf = formatarCpf(req.cpf());
        if (cpf == null) throw new RegraDeNegocioException("CPF deve ter 11 dígitos.");
        Profissional porCpf = profissionalRepo.findFirstByCpf(cpf).orElse(null);
        Profissional p;
        boolean novo = false;
        if (req.profissionalId() != null) {
            p = carregarProfissional(req.profissionalId());
            if (porCpf != null && !porCpf.getId().equals(p.getId())) {
                throw new RegraDeNegocioException("O CPF informado já está cadastrado para " + porCpf.getNome()
                        + ". Busque esse profissional pelo CPF.");
            }
        } else if (porCpf != null) {
            p = porCpf;
        } else {
            p = Profissional.builder().cadastradoPor(nomeUsuarioAtual()).build();
            novo = true;
        }
        p.setAtivo(true);
        p.setNome(req.nome().trim().toUpperCase());
        p.setMatricula(req.matricula().trim());
        p.setCargo(req.cargo().trim());
        p.setLotacao(req.lotacao().trim());
        p.setCpf(cpf);
        p.setRg(limpar(req.rg()));
        p.setOrgaoExpedidor(req.orgaoExpedidor().trim());
        if (req.nivelId() != null) {
            p.setNivel(nivelRepo.findById(req.nivelId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Nível de diária", req.nivelId())));
        }
        p.setBanco(req.banco().trim());
        p.setAgencia(req.agencia().trim());
        p.setContaCorrente(req.contaCorrente().trim().toUpperCase());
        p.setPix(req.pix().trim());
        p = profissionalRepo.save(p);
        if (novo) {
            auditoriaService.registrar(AcaoAuditoria.CREATE, "Profissional", p.getId(),
                    "Profissional cadastrado pelo requerimento de diária: " + p.getNome());
        }
        return p;
    }

    /** Número informado (único) ou, na falta, o que a diária já tem ou o próximo da sequência do ano. */
    private String numeroPara(Diaria d, DiariaRequest req, LocalDate dataRequerimento) {
        String informado = limpar(req.numeroRequerimento());
        if (informado != null) {
            diariaRepo.findFirstByNumeroRequerimento(informado).filter(outra -> !outra.getId().equals(d.getId()))
                    .ifPresent(outra -> { throw new RegraDeNegocioException("Já existe um requerimento com o número " + informado + "."); });
            return informado;
        }
        if (d.getNumeroRequerimento() != null) return d.getNumeroRequerimento();
        int ano = dataRequerimento.getYear();
        int maior = 0;
        for (String numero : diariaRepo.numerosComSufixo("%/" + ano)) {
            try {
                maior = Math.max(maior, Integer.parseInt(numero.substring(0, numero.indexOf('/'))));
            } catch (RuntimeException ignorado) { /* número fora do padrão sequência/ano */ }
        }
        return (maior + 1) + "/" + ano;
    }

    /** Apura a quantidade pelas datas/horários e copia para a diária os dados do profissional. */
    private static void aplicar(Diaria d, DiariaRequest req, Profissional p) {
        LocalDateTime saida = req.partida().atTime(req.horaSaida());
        LocalDateTime retorno = req.retorno().atTime(req.horaChegada());
        CalculoDiarias.Resultado calculo = CalculoDiarias.calcular(saida, retorno);
        if (calculo.quantidade().signum() <= 0) {
            throw new RegraDeNegocioException("O afastamento informado não gera diária: tem menos de 24 horas e não "
                    + "abrange o horário de almoço (12h) nem o de jantar (18h).");
        }
        d.setProfissional(p);
        d.setProfNome(p.getNome());
        d.setProfMatricula(p.getMatricula());
        d.setProfCargo(p.getCargo());
        d.setProfLotacao(p.getLotacao());
        d.setProfCpf(p.getCpf());
        d.setProfRg(p.getRg());
        d.setProfOrgaoExpedidor(p.getOrgaoExpedidor());
        d.setBanco(p.getBanco());
        d.setAgencia(p.getAgencia());
        d.setContaCorrente(p.getContaCorrente());
        d.setPix(p.getPix());
        d.setDadosBancarios(null);
        d.setOrigem(req.origem().trim());
        d.setItinerario(req.itinerario().trim());
        d.setPartida(req.partida());
        d.setHoraSaida(req.horaSaida());
        d.setRetorno(req.retorno());
        d.setHoraChegada(req.horaChegada());
        d.setMeioTransporte(req.meioTransporte());
        d.setRecurso(req.recurso());
        d.setTipoDiaria(req.tipoDiaria());
        BigDecimal unitario = req.valorUnitario().setScale(2, RoundingMode.HALF_UP);
        d.setNumeroDiarias(calculo.quantidade());
        d.setValorUnitario(unitario);
        d.setValorTotal(calculo.quantidade().multiply(unitario).setScale(2, RoundingMode.HALF_UP));
        d.setFinalidade(req.finalidade().trim());
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

    private static String truncar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Resumo dos campos que o deferimento e o pagamento dependem, para o histórico. */
    private static String resumo(Diaria d) {
        String prof = d.getProfNome() != null ? d.getProfNome()
                : d.getProfissional() != null ? d.getProfissional().getNome() : "?";
        return "%s; %s x R$ %s = R$ %s; %s a %s; %s".formatted(prof, d.getNumeroDiarias(), d.getValorUnitario(),
                d.getValorTotal(), d.getPartida(), d.getRetorno(), d.getItinerario());
    }
}

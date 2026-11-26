package com.fundacao.gerenciador_patrimonial.service.dominio;

import com.fundacao.gerenciador_patrimonial.config.DominioProperties;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.unboundid.ldap.sdk.*;
import com.unboundid.util.ssl.JVMDefaultTrustManager;
import com.unboundid.util.ssl.PEMFileTrustManager;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.SocketFactory;
import javax.net.ssl.TrustManager;
import java.io.File;
import java.util.*;

/**
 * Acesso ao Active Directory (Samba AD do domínio FPTO.LOCAL) pela conta de serviço.
 *
 * <p>Login: a conta de serviço acha o usuário pelo {@code sAMAccountName} e a senha é
 * conferida com um bind do próprio usuário (a senha nunca é guardada). Grupos: o perfil
 * sai dos grupos {@link GrupoDominio} em {@code app.ad.ou-grupos}, lidos pelo atributo
 * {@code member} do grupo (não depende de {@code memberOf}).</p>
 *
 * <p>Toda operação abre e fecha a própria conexão: o volume é pequeno e assim não há
 * conexão presa quando o AD reinicia.</p>
 */
@Component
@Slf4j
public class DiretorioAd {

    private static final String[] ATRIBUTOS_CONTA = {"sAMAccountName", "displayName", "cn", "mail", "userAccountControl"};
    /** Grupo de segurança global (ADS_GROUP_TYPE_GLOBAL_GROUP | ADS_GROUP_TYPE_SECURITY_ENABLED). */
    private static final String GRUPO_SEGURANCA_GLOBAL = "-2147483646";
    private static final int CONTA_DESATIVADA = 0x2;

    private final DominioProperties props;
    private volatile ServerSet servidores;

    public DiretorioAd(DominioProperties props) {
        this.props = props;
    }

    /** Conta de usuário encontrada no AD. */
    public record ContaAd(String dn, String login, String nome, String email, boolean ativa) {}

    /** Conta autenticada e os grupos do Patrimônio em que ela está. */
    public record ResultadoLogin(ContaAd conta, Set<GrupoDominio> grupos) {}

    /** Grupo do Patrimônio como está no AD ({@code dn} nulo = ainda não criado). */
    public record GrupoNoAd(GrupoDominio grupo, String nome, String dn, Set<String> membros) {
        public boolean existe() { return dn != null; }
    }

    /** Conta da rede com os grupos do Patrimônio dela. */
    public record ContaComGrupos(ContaAd conta, Set<GrupoDominio> grupos) {}

    /** O AD não respondeu ou recusou a conta de serviço. */
    public static class DiretorioIndisponivelException extends RuntimeException {
        public DiretorioIndisponivelException(String mensagem, Throwable causa) { super(mensagem, causa); }
    }

    public boolean habilitado() {
        return props.habilitado();
    }

    public DominioProperties config() {
        return props;
    }

    // =========================================================================
    // Login
    // =========================================================================

    /**
     * Normaliza o que a pessoa digitou ({@code joao}, {@code FPTO\joao}, {@code joao@fpto.local})
     * para o {@code sAMAccountName} em minúsculas. Nulo quando não é um login de rede válido
     * (ex.: e-mail de outro domínio).
     */
    public String normalizarLogin(String digitado) {
        if (digitado == null) return null;
        String l = digitado.trim().toLowerCase(Locale.ROOT);
        int barra = l.lastIndexOf('\\');
        if (barra >= 0) l = l.substring(barra + 1);
        int arroba = l.indexOf('@');
        if (arroba >= 0) {
            String dominio = l.substring(arroba + 1);
            if (props.dominio() != null && !props.dominio().isBlank() && !dominio.equalsIgnoreCase(props.dominio().trim())) return null;
            l = l.substring(0, arroba);
        }
        return l.matches("[a-z0-9._-]{1,60}") ? l : null;
    }

    /**
     * Confere usuário e senha no AD.
     *
     * @return vazio quando a conta não existe, está desativada no AD ou a senha não confere
     * @throws DiretorioIndisponivelException quando o AD não responde ou recusa a conta de serviço
     */
    public Optional<ResultadoLogin> autenticar(String login, String senha) {
        // Bind simples com senha vazia é um bind ANÔNIMO e "dá certo": nunca aceitar.
        if (login == null || senha == null || senha.isEmpty()) return Optional.empty();
        try (LDAPConnection c = conectarComoServico()) {
            Optional<ContaAd> conta = buscar(c, login);
            if (conta.isEmpty() || !conta.get().ativa()) return Optional.empty();
            try (LDAPConnection doUsuario = servidores().getConnection()) {
                doUsuario.bind(new SimpleBindRequest(conta.get().dn(), senha));
            } catch (LDAPException e) {
                if (e.getResultCode() == ResultCode.INVALID_CREDENTIALS) return Optional.empty();
                throw indisponivel("conferir a senha", e);
            }
            return Optional.of(new ResultadoLogin(conta.get(), gruposDe(c, conta.get().dn())));
        }
    }

    // =========================================================================
    // Consultas
    // =========================================================================

    public Optional<ContaAd> buscar(String login) {
        try (LDAPConnection c = conectarComoServico()) {
            return buscar(c, login);
        }
    }

    /** Contas de pessoas (não computadores) cujo login ou nome contém o termo; vazio lista todas. */
    public List<ContaComGrupos> pesquisar(String termo, int limite) {
        Filter filtro = filtroPessoas(termo == null || termo.isBlank() ? null : Filter.createORFilter(
                Filter.createSubstringFilter("sAMAccountName", null, new String[]{termo.trim()}, null),
                Filter.createSubstringFilter("displayName", null, new String[]{termo.trim()}, null),
                Filter.createSubstringFilter("cn", null, new String[]{termo.trim()}, null)));
        try (LDAPConnection c = conectarComoServico()) {
            List<GrupoNoAd> grupos = grupos(c);
            SearchRequest req = new SearchRequest(props.base(), SearchScope.SUB, filtro, ATRIBUTOS_CONTA);
            req.setSizeLimit(limite);
            List<SearchResultEntry> entradas;
            try {
                entradas = c.search(req).getSearchEntries();
            } catch (LDAPSearchException e) {
                if (e.getResultCode() != ResultCode.SIZE_LIMIT_EXCEEDED) throw indisponivel("pesquisar usuários", e);
                entradas = e.getSearchEntries();
            }
            return entradas.stream()
                    .map(this::conta)
                    .filter(Objects::nonNull)
                    .map(conta -> new ContaComGrupos(conta, gruposQueContem(grupos, conta.dn())))
                    .sorted(Comparator.comparing(cg -> cg.conta().nome().toLowerCase(Locale.ROOT)))
                    .toList();
        }
    }

    /** Os grupos do Patrimônio como estão no AD (existentes e faltantes, com os membros). */
    public List<GrupoNoAd> grupos() {
        try (LDAPConnection c = conectarComoServico()) {
            return grupos(c);
        }
    }

    // =========================================================================
    // Alterações no AD
    // =========================================================================

    /**
     * Deixa a conta exatamente nos grupos {@code desejados} (entre os grupos do Patrimônio):
     * inclui nos que faltam e tira dos demais. Outros grupos do AD não são tocados.
     */
    public void definirGrupos(String login, Set<GrupoDominio> desejados) {
        try (LDAPConnection c = conectarComoServico()) {
            ContaAd conta = buscar(c, login).orElseThrow(() ->
                    new RegraDeNegocioException("Usuário " + login + " não existe no AD."));
            for (GrupoNoAd g : grupos(c)) {
                boolean deveEstar = desejados.contains(g.grupo());
                boolean esta = contem(g.membros(), conta.dn());
                if (deveEstar == esta) continue;
                if (!g.existe()) {
                    throw new RegraDeNegocioException("O grupo " + g.nome() + " não existe no AD. Crie os grupos que faltam antes.");
                }
                try {
                    c.modify(g.dn(), new Modification(deveEstar ? ModificationType.ADD : ModificationType.DELETE, "member", conta.dn()));
                    log.info("AD: {} {} o grupo {}", login, deveEstar ? "entrou em" : "saiu de", g.nome());
                } catch (LDAPException e) {
                    if (e.getResultCode() == ResultCode.INSUFFICIENT_ACCESS_RIGHTS) {
                        throw new RegraDeNegocioException("A conta de serviço não tem permissão para alterar o grupo "
                                + g.nome() + " no AD (veja docs/INTEGRACAO_AD.md).");
                    }
                    throw indisponivel("alterar o grupo " + g.nome(), e);
                }
            }
        }
    }

    /** Cria na OU os grupos do Patrimônio que ainda não existem; devolve os nomes criados. */
    public List<String> criarGruposFaltantes() {
        List<String> criados = new ArrayList<>();
        try (LDAPConnection c = conectarComoServico()) {
            for (GrupoNoAd g : grupos(c)) {
                if (g.existe()) continue;
                String dn = new DN(new RDN("CN", g.nome()), new DN(props.ouGrupos())).toString();
                try {
                    c.add(dn,
                            new Attribute("objectClass", "top", "group"),
                            new Attribute("cn", g.nome()),
                            new Attribute("sAMAccountName", g.nome()),
                            new Attribute("groupType", GRUPO_SEGURANCA_GLOBAL),
                            new Attribute("description", g.grupo().descricao()));
                    criados.add(g.nome());
                    log.info("AD: grupo {} criado em {}", g.nome(), props.ouGrupos());
                } catch (LDAPException e) {
                    if (e.getResultCode() == ResultCode.INSUFFICIENT_ACCESS_RIGHTS) {
                        throw new RegraDeNegocioException("A conta de serviço não tem permissão para criar grupos em "
                                + props.ouGrupos() + " (veja docs/INTEGRACAO_AD.md).");
                    }
                    if (e.getResultCode() == ResultCode.NO_SUCH_OBJECT) {
                        throw new RegraDeNegocioException("A OU " + props.ouGrupos() + " não existe no AD.");
                    }
                    throw indisponivel("criar o grupo " + g.nome(), e);
                }
            }
        } catch (LDAPException e) {
            throw new RegraDeNegocioException("OU dos grupos inválida: " + props.ouGrupos());
        }
        return criados;
    }

    /** Testa a conexão e a conta de serviço (para a tela de status). */
    public void testarConexao() {
        try (LDAPConnection ignored = conectarComoServico()) {
            // abrir e autenticar já é o teste
        }
    }

    // =========================================================================
    // internos
    // =========================================================================

    private Optional<ContaAd> buscar(LDAPConnection c, String login) {
        SearchRequest req = new SearchRequest(props.base(), SearchScope.SUB,
                filtroPessoas(Filter.createEqualityFilter("sAMAccountName", login)), ATRIBUTOS_CONTA);
        req.setSizeLimit(2);
        try {
            return c.search(req).getSearchEntries().stream().map(this::conta).filter(Objects::nonNull).findFirst();
        } catch (LDAPException e) {
            throw indisponivel("buscar o usuário", e);
        }
    }

    private List<GrupoNoAd> grupos(LDAPConnection c) {
        List<String> nomes = Arrays.stream(GrupoDominio.values()).map(g -> g.nome(props.prefixoGrupo())).toList();
        Filter filtro = Filter.createANDFilter(Filter.createEqualityFilter("objectClass", "group"),
                Filter.createORFilter(nomes.stream().map(n -> Filter.createEqualityFilter("cn", n)).toList()));
        Map<GrupoDominio, SearchResultEntry> achados = new EnumMap<>(GrupoDominio.class);
        try {
            for (SearchResultEntry e : c.search(props.ouGrupos(), SearchScope.SUB, filtro, "cn", "member").getSearchEntries()) {
                GrupoDominio.doNome(props.prefixoGrupo(), e.getAttributeValue("cn")).ifPresent(g -> achados.put(g, e));
            }
        } catch (LDAPException e) {
            if (e.getResultCode() != ResultCode.NO_SUCH_OBJECT) throw indisponivel("ler os grupos", e);
            // OU ainda não existe: todos os grupos aparecem como faltantes
        }
        List<GrupoNoAd> lista = new ArrayList<>();
        for (GrupoDominio g : GrupoDominio.values()) {
            SearchResultEntry e = achados.get(g);
            Set<String> membros = new HashSet<>();
            if (e != null && e.getAttributeValues("member") != null) {
                for (String m : e.getAttributeValues("member")) membros.add(normalizarDn(m));
            }
            lista.add(new GrupoNoAd(g, g.nome(props.prefixoGrupo()), e == null ? null : e.getDN(), membros));
        }
        return lista;
    }

    private Set<GrupoDominio> gruposDe(LDAPConnection c, String dn) {
        return gruposQueContem(grupos(c), dn);
    }

    private static Set<GrupoDominio> gruposQueContem(List<GrupoNoAd> grupos, String dn) {
        Set<GrupoDominio> set = EnumSet.noneOf(GrupoDominio.class);
        for (GrupoNoAd g : grupos) if (contem(g.membros(), dn)) set.add(g.grupo());
        return set;
    }

    private static boolean contem(Set<String> membros, String dn) {
        return membros.contains(normalizarDn(dn));
    }

    private static String normalizarDn(String dn) {
        try {
            return DN.normalize(dn);
        } catch (LDAPException e) {
            return dn.toLowerCase(Locale.ROOT);
        }
    }

    /** Pessoas: {@code objectClass=user} que não sejam contas de computador. */
    private static Filter filtroPessoas(Filter extra) {
        List<Filter> partes = new ArrayList<>(List.of(
                Filter.createEqualityFilter("objectClass", "user"),
                Filter.createNOTFilter(Filter.createEqualityFilter("objectClass", "computer"))));
        if (extra != null) partes.add(extra);
        return Filter.createANDFilter(partes);
    }

    private ContaAd conta(SearchResultEntry e) {
        String login = e.getAttributeValue("sAMAccountName");
        if (login == null) return null;
        String nome = Optional.ofNullable(e.getAttributeValue("displayName"))
                .or(() -> Optional.ofNullable(e.getAttributeValue("cn"))).orElse(login);
        Integer uac = e.getAttributeValueAsInteger("userAccountControl");
        boolean ativa = uac == null || (uac & CONTA_DESATIVADA) == 0;
        return new ContaAd(e.getDN(), login.toLowerCase(Locale.ROOT), nome, e.getAttributeValue("mail"), ativa);
    }

    private LDAPConnection conectarComoServico() {
        if (!props.habilitado()) throw new RegraDeNegocioException("O login pela rede (AD) está desligado (AD_HABILITADO).");
        if (vazio(props.usuarioServico()) || vazio(props.senhaServico())) {
            throw new DiretorioIndisponivelException("Conta de serviço do AD não configurada (AD_USUARIO_SERVICO / AD_SENHA_SERVICO).", null);
        }
        LDAPConnection c = null;
        try {
            c = servidores().getConnection();
            c.bind(props.usuarioServico(), props.senhaServico());
            return c;
        } catch (LDAPException e) {
            if (c != null) c.close();
            if (e.getResultCode() == ResultCode.INVALID_CREDENTIALS) {
                throw new DiretorioIndisponivelException("O AD recusou a conta de serviço (usuário/senha de AD_USUARIO_SERVICO).", e);
            }
            throw indisponivel("conectar", e);
        }
    }

    private ServerSet servidores() {
        ServerSet s = servidores;
        if (s != null) return s;
        synchronized (this) {
            if (servidores == null) servidores = montarServidores();
            return servidores;
        }
    }

    private ServerSet montarServidores() {
        if (props.urls().isEmpty()) throw new DiretorioIndisponivelException("Nenhum servidor do AD configurado (AD_URLS).", null);
        List<String> hosts = new ArrayList<>();
        List<Integer> portas = new ArrayList<>();
        Boolean seguro = null;
        for (String u : props.urls()) {
            try {
                LDAPURL url = new LDAPURL(u);
                boolean ldaps = "ldaps".equalsIgnoreCase(url.getScheme());
                if (seguro != null && seguro != ldaps) {
                    throw new DiretorioIndisponivelException("AD_URLS mistura ldap:// e ldaps://; use um só.", null);
                }
                seguro = ldaps;
                hosts.add(url.getHost());
                portas.add(url.getPort());
            } catch (LDAPException e) {
                throw new DiretorioIndisponivelException("URL do AD inválida: " + u, e);
            }
        }
        LDAPConnectionOptions opcoes = new LDAPConnectionOptions();
        opcoes.setConnectTimeoutMillis(props.timeoutMs());
        opcoes.setResponseTimeoutMillis(props.timeoutMs());
        SocketFactory fabrica = Boolean.TRUE.equals(seguro) ? fabricaTls() : SocketFactory.getDefault();
        if (!Boolean.TRUE.equals(seguro)) {
            log.warn("Login pela rede usando LDAP sem TLS ({}): as senhas trafegam abertas. Use ldaps:// em produção.", props.urls());
        }
        return new FailoverServerSet(hosts.toArray(String[]::new), portas.stream().mapToInt(Integer::intValue).toArray(), fabrica, opcoes);
    }

    private SocketFactory fabricaTls() {
        try {
            TrustManager confianca;
            if (props.confiarEmQualquerCertificado()) {
                log.warn("AD_CONFIAR_EM_QUALQUER_CERTIFICADO ligado: o certificado do AD NÃO é verificado. Só para testes.");
                confianca = new TrustAllTrustManager();
            } else if (!vazio(props.certificadoCa())) {
                confianca = new PEMFileTrustManager(new File(props.certificadoCa()));
            } else {
                confianca = JVMDefaultTrustManager.getInstance();
            }
            return new SSLUtil(confianca).createSSLSocketFactory();
        } catch (Exception e) {
            throw new DiretorioIndisponivelException("Não foi possível preparar o TLS do AD (AD_CERTIFICADO_CA): " + e.getMessage(), e);
        }
    }

    private DiretorioIndisponivelException indisponivel(String oQue, LDAPException e) {
        log.warn("AD: falha ao {}: {} ({})", oQue, e.getResultCode(), e.getDiagnosticMessage() != null ? e.getDiagnosticMessage() : e.getMessage());
        return new DiretorioIndisponivelException("O servidor da rede (AD) não respondeu ao " + oQue + ". Tente de novo em instantes.", e);
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}

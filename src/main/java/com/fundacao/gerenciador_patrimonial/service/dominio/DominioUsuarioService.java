package com.fundacao.gerenciador_patrimonial.service.dominio;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.domain.enums.OrigemUsuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.Perfil;
import com.fundacao.gerenciador_patrimonial.dto.response.DominioStatusResponse;
import com.fundacao.gerenciador_patrimonial.dto.response.UsuarioDominioResponse;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import com.fundacao.gerenciador_patrimonial.service.AuditoriaService;
import com.fundacao.gerenciador_patrimonial.service.dominio.DiretorioAd.ContaAd;
import com.fundacao.gerenciador_patrimonial.service.dominio.DiretorioAd.DiretorioIndisponivelException;
import com.fundacao.gerenciador_patrimonial.service.dominio.DiretorioAd.ResultadoLogin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Usuários da rede (AD FPTO.LOCAL) no Patrimônio.
 *
 * <ul>
 *   <li><b>Login</b>: quem entra com a conta da rede ganha (ou atualiza) um cadastro
 *       {@link OrigemUsuario#DOMINIO}; o perfil é o dos grupos do Patrimônio no AD e, sem
 *       grupo, {@link Perfil#USUARIO} (usuário padrão, sem acesso aos módulos).</li>
 *   <li><b>Migração</b>: um cadastro LOCAL antigo com o mesmo login vira DOMINIO no primeiro
 *       login pela rede. Se ele ainda não estiver em nenhum grupo, o perfil que já tinha é
 *       gravado no AD, para ninguém perder o acesso na troca. Se o AD recusar a gravação,
 *       o cadastro continua LOCAL e a conversão é tentada de novo no próximo login.</li>
 *   <li><b>Gestão</b>: o administrador escolhe o grupo na tela "Grupos da rede" e o sistema
 *       grava no AD e no cadastro (o que encerra a sessão da pessoa, pelo SessaoUsuarioFilter).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DominioUsuarioService {

    /** {@code senha_hash} dos usuários da rede: não é BCrypt, então nunca confere com senha nenhuma. */
    public static final String SENHA_DO_DOMINIO = "{dominio}";
    private static final int LIMITE_PESQUISA = 300;

    private final DiretorioAd diretorio;
    private final UsuarioRepository usuarioRepo;
    private final AuditoriaService auditoria;

    // =========================================================================
    // Login
    // =========================================================================

    /** Cria/atualiza o cadastro de quem acabou de entrar com a senha da rede. */
    @Transactional
    public Usuario sincronizarNoLogin(ResultadoLogin login) {
        ContaAd conta = login.conta();
        Perfil perfilAd = GrupoDominio.perfilDe(login.grupos());
        boolean abastecerAd = login.grupos().contains(GrupoDominio.ABASTECER);

        Usuario u = usuarioRepo.findByLogin(conta.login()).orElse(null);
        if (u == null) {
            u = usuarioRepo.save(Usuario.builder()
                    .nomeCompleto(nome(conta))
                    .login(conta.login())
                    .senhaHash(SENHA_DO_DOMINIO)
                    .origem(OrigemUsuario.DOMINIO)
                    .perfil(perfilAd)
                    .podeAbastecer(abastecerAd)
                    .ativo(true)
                    .build());
            auditoria.registrar(AcaoAuditoria.GRUPO_DOMINIO, "Usuario", u.getId(),
                    "Primeiro login pela rede: " + conta.login() + " entrou como " + perfilAd);
            return u;
        }

        boolean convertido = false;
        if (u.getOrigem() == OrigemUsuario.LOCAL) {
            boolean semGrupo = perfilAd == Perfil.USUARIO && !abastecerAd;
            if (semGrupo && (u.getPerfil() != Perfil.USUARIO || u.isPodeAbastecer())) {
                // Leva para o AD o acesso que a pessoa já tinha no cadastro local.
                try {
                    diretorio.definirGrupos(conta.login(), GrupoDominio.para(u.getPerfil(), u.isPodeAbastecer()));
                } catch (RuntimeException e) {
                    log.warn("Login pela rede de {}: não foi possível gravar o perfil {} no AD ({}); cadastro continua LOCAL.",
                            conta.login(), u.getPerfil(), e.getMessage());
                    return u;
                }
                perfilAd = u.getPerfil();
                abastecerAd = u.isPodeAbastecer();
            }
            u.setOrigem(OrigemUsuario.DOMINIO);
            u.setSenhaHash(SENHA_DO_DOMINIO);
            u.setSenhaProvisoria(false);
            convertido = true;
            auditoria.registrar(AcaoAuditoria.GRUPO_DOMINIO, "Usuario", u.getId(),
                    "Cadastro local de " + conta.login() + " passou a usar a senha da rede (perfil " + perfilAd + ")");
        }

        boolean mudou = u.getPerfil() != perfilAd || u.isPodeAbastecer() != abastecerAd
                || !Objects.equals(u.getNomeCompleto(), nome(conta)) || u.isSenhaProvisoria();
        u.setPerfil(perfilAd);
        u.setPodeAbastecer(abastecerAd);
        u.setNomeCompleto(nome(conta));
        u.setSenhaProvisoria(false);
        return mudou || convertido ? usuarioRepo.save(u) : u;
    }

    // =========================================================================
    // Tela "Grupos da rede"
    // =========================================================================

    public DominioStatusResponse status() {
        var cfg = diretorio.config();
        if (!diretorio.habilitado()) {
            return new DominioStatusResponse(false, cfg.dominio(), cfg.urls(), cfg.ouGrupos(), false,
                    "Login pela rede desligado (AD_HABILITADO=false).", List.of());
        }
        try {
            List<DominioStatusResponse.Grupo> grupos = diretorio.grupos().stream()
                    .map(g -> new DominioStatusResponse.Grupo(
                            g.grupo().perfil() != null ? g.grupo().perfil().name() : g.grupo().name(),
                            g.nome(), g.grupo().descricao(), g.existe(), g.membros().size()))
                    .toList();
            return new DominioStatusResponse(true, cfg.dominio(), cfg.urls(), cfg.ouGrupos(), true, null, grupos);
        } catch (DiretorioIndisponivelException | RegraDeNegocioException e) {
            return new DominioStatusResponse(true, cfg.dominio(), cfg.urls(), cfg.ouGrupos(), false, e.getMessage(), List.of());
        }
    }

    public DominioStatusResponse criarGrupos() {
        List<String> criados = diretorio.criarGruposFaltantes();
        if (!criados.isEmpty()) {
            auditoria.registrar(AcaoAuditoria.GRUPO_DOMINIO, "Dominio", null, "Grupos criados no AD: " + String.join(", ", criados));
        }
        return status();
    }

    @Transactional(readOnly = true)
    public List<UsuarioDominioResponse> listar(String termo) {
        List<DiretorioAd.ContaComGrupos> contas = diretorio.pesquisar(termo, LIMITE_PESQUISA);
        Map<String, Usuario> cadastros = usuarioRepo.findAll().stream()
                .collect(Collectors.toMap(Usuario::getLogin, Function.identity(), (a, b) -> a));
        return contas.stream().map(cg -> resposta(cg.conta(), cg.grupos(), cadastros.get(cg.conta().login()))).toList();
    }

    /**
     * Grava no AD o grupo de perfil (e o de abastecimento) da conta e atualiza o cadastro.
     * {@code perfil} nulo ou USUARIO tira a pessoa de todos os grupos de perfil.
     */
    @Transactional
    public UsuarioDominioResponse definirAcesso(String login, Perfil perfil, boolean podeAbastecer) {
        Perfil alvo = perfil == null ? Perfil.USUARIO : perfil;
        if (alvo == Perfil.INTEGRACAO) throw new RegraDeNegocioException("O perfil INTEGRACAO é só para contas técnicas locais.");
        String normalizado = diretorio.normalizarLogin(login);
        if (normalizado == null) throw new RecursoNaoEncontradoException("Login de rede inválido: " + login);
        if (normalizado.equals(loginAtual()) && alvo != Perfil.ADMINISTRADOR) {
            throw new RegraDeNegocioException("Você não pode tirar o seu próprio acesso de administrador.");
        }

        ContaAd conta = diretorio.buscar(normalizado)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário " + normalizado + " não existe no AD."));
        Set<GrupoDominio> grupos = GrupoDominio.para(alvo, podeAbastecer);
        diretorio.definirGrupos(conta.login(), grupos);

        Usuario u = usuarioRepo.findByLogin(conta.login()).orElseGet(() -> Usuario.builder()
                .login(conta.login()).ativo(true).build());
        Perfil antes = u.getPerfil();
        u.setNomeCompleto(nome(conta));
        u.setOrigem(OrigemUsuario.DOMINIO);
        u.setSenhaHash(SENHA_DO_DOMINIO);
        u.setSenhaProvisoria(false);
        u.setPerfil(alvo);
        u.setPodeAbastecer(podeAbastecer);
        u = usuarioRepo.save(u);
        auditoria.registrar(AcaoAuditoria.GRUPO_DOMINIO, "Usuario", u.getId(),
                "Acesso de " + conta.login() + " no AD: " + (antes == null ? "-" : antes) + " → " + alvo
                        + (podeAbastecer ? " + abastecer" : "") + " (grupos: " + grupos.stream()
                        .map(g -> g.nome(diretorio.config().prefixoGrupo())).collect(Collectors.joining(", ")) + ")");
        return resposta(conta, grupos, u);
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private static UsuarioDominioResponse resposta(ContaAd conta, Set<GrupoDominio> grupos, Usuario cadastro) {
        return new UsuarioDominioResponse(conta.login(), conta.nome(), conta.email(), conta.ativa(),
                GrupoDominio.perfilDe(grupos), grupos.contains(GrupoDominio.ABASTECER),
                cadastro == null ? null : cadastro.getId(),
                cadastro == null ? null : cadastro.isAtivo(),
                cadastro == null ? null : cadastro.getOrigem());
    }

    private static String nome(ContaAd conta) {
        String n = conta.nome() == null || conta.nome().isBlank() ? conta.login() : conta.nome().trim();
        return n.length() > 150 ? n.substring(0, 150) : n;
    }

    private static String loginAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}

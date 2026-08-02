package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.domain.enums.AcaoAuditoria;
import com.fundacao.gerenciador_patrimonial.dto.request.TrocarSenhaRequest;
import com.fundacao.gerenciador_patrimonial.dto.request.UsuarioRequest;
import com.fundacao.gerenciador_patrimonial.dto.response.UsuarioResponse;
import com.fundacao.gerenciador_patrimonial.exception.RecursoNaoEncontradoException;
import com.fundacao.gerenciador_patrimonial.exception.RegraDeNegocioException;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço de gestão de usuários do sistema.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Senha é sempre armazenada como hash BCrypt.</li>
 *   <li>Login é único (verificado antes de persistir).</li>
 *   <li>Na edição, senha vazia = mantém a atual.</li>
 *   <li>Troca de senha pelo próprio usuário exige a senha atual.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final String ENT = "Usuario";

    private final UsuarioRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    // =========================================================================
    // CRUD básico
    // =========================================================================

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(Pageable pageable) {
        return repo.findAll(pageable).map(UsuarioResponse::from);
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscarPorId(Long id) {
        return UsuarioResponse.from(buscarEntidade(id));
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest req) {
        if (req.senha() == null || req.senha().isBlank()) {
            throw new RegraDeNegocioException("Senha é obrigatória na criação do usuário.");
        }
        if (repo.existsByLogin(req.login())) {
            throw new RegraDeNegocioException("Login já está em uso: " + req.login());
        }

        Usuario u = Usuario.builder()
                .nomeCompleto(req.nomeCompleto().trim())
                .login(req.login().trim().toLowerCase())
                .senhaHash(passwordEncoder.encode(req.senha()))
                .perfil(req.perfil())
                .ativo(req.ativo() == null || req.ativo())
                .build();
        Usuario salvo = repo.save(u);
        auditoriaService.registrar(AcaoAuditoria.CREATE, ENT, salvo.getId(),
                "Cadastro: nome=%s, login=%s, perfil=%s, ativo=%s".formatted(
                        salvo.getNomeCompleto(), salvo.getLogin(), salvo.getPerfil(), salvo.isAtivo()));
        return UsuarioResponse.from(salvo);
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioRequest req) {
        Usuario u = buscarEntidade(id);

        // Se mudou o login, garantir unicidade
        String novoLogin = req.login().trim().toLowerCase();
        if (!u.getLogin().equals(novoLogin) && repo.existsByLogin(novoLogin)) {
            throw new RegraDeNegocioException("Login já está em uso: " + novoLogin);
        }

        String nomeAntes = u.getNomeCompleto();
        String loginAntes = u.getLogin();
        var perfilAntes = u.getPerfil();
        boolean ativoAntes = u.isAtivo();
        boolean trocouSenha = req.senha() != null && !req.senha().isBlank();

        u.setNomeCompleto(req.nomeCompleto().trim());
        u.setLogin(novoLogin);
        u.setPerfil(req.perfil());
        if (req.ativo() != null) u.setAtivo(req.ativo());

        if (trocouSenha) {
            u.setSenhaHash(passwordEncoder.encode(req.senha()));
        }
        // Entidade managed: o dirty checking persiste no commit — save() seria um merge redundante.
        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT, id,
                "Nome: %s → %s; Login: %s → %s; Perfil: %s → %s; Ativo: %s → %s; Senha alterada: %s".formatted(
                        nomeAntes, u.getNomeCompleto(),
                        loginAntes, u.getLogin(),
                        perfilAntes, u.getPerfil(),
                        ativoAntes, u.isAtivo(),
                        trocouSenha ? "sim" : "não"));
        return UsuarioResponse.from(u);
    }

    /** Inativa em vez de deletar — preserva auditoria. */
    @Transactional
    public void inativar(Long id) {
        Usuario u = buscarEntidade(id);
        u.setAtivo(false);
        auditoriaService.registrar(AcaoAuditoria.DELETE, ENT, id,
                "Inativação: login=%s, nome=%s".formatted(u.getLogin(), u.getNomeCompleto()));
    }

    // =========================================================================
    // Troca de senha pelo próprio usuário
    // =========================================================================

    @Transactional
    public void trocarSenha(String login, TrocarSenhaRequest req) {
        if (!req.novaSenha().equals(req.confirmacao())) {
            throw new RegraDeNegocioException("Confirmação não confere com a nova senha.");
        }
        Usuario u = repo.findByLogin(login)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Usuário não encontrado: " + login));

        if (!passwordEncoder.matches(req.senhaAtual(), u.getSenhaHash())) {
            throw new RegraDeNegocioException("Senha atual incorreta.");
        }
        u.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
        auditoriaService.registrar(AcaoAuditoria.UPDATE, ENT, u.getId(),
                "Troca de senha pelo próprio usuário (login=%s)".formatted(login));
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private Usuario buscarEntidade(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Usuário não encontrado: id=" + id));
    }
}

package com.fundacao.gerenciador_patrimonial.service;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
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

    private final UsuarioRepository repo;
    private final PasswordEncoder passwordEncoder;

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
        return UsuarioResponse.from(repo.save(u));
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioRequest req) {
        Usuario u = buscarEntidade(id);

        // Se mudou o login, garantir unicidade
        String novoLogin = req.login().trim().toLowerCase();
        if (!u.getLogin().equals(novoLogin) && repo.existsByLogin(novoLogin)) {
            throw new RegraDeNegocioException("Login já está em uso: " + novoLogin);
        }

        u.setNomeCompleto(req.nomeCompleto().trim());
        u.setLogin(novoLogin);
        u.setPerfil(req.perfil());
        if (req.ativo() != null) u.setAtivo(req.ativo());

        // Senha só é alterada se informada
        if (req.senha() != null && !req.senha().isBlank()) {
            u.setSenhaHash(passwordEncoder.encode(req.senha()));
        }
        return UsuarioResponse.from(repo.save(u));
    }

    /** Inativa em vez de deletar — preserva auditoria. */
    @Transactional
    public void inativar(Long id) {
        Usuario u = buscarEntidade(id);
        u.setAtivo(false);
        repo.save(u);
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
        repo.save(u);
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private Usuario buscarEntidade(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new RecursoNaoEncontradoException("Usuário não encontrado: id=" + id));
    }
}

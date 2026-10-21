package com.fundacao.gerenciador_patrimonial.security;

import com.fundacao.gerenciador_patrimonial.domain.entity.Usuario;
import com.fundacao.gerenciador_patrimonial.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Revalida o usuário autenticado a cada requisição da API.
 *
 * <ul>
 *   <li>A sessão do SPA guarda um retrato do usuário feito no login. Se no banco ele foi
 *       desativado, mudou de perfil ou de permissão extra ou teve a senha trocada/redefinida, a sessão é
 *       encerrada e a requisição recebe 401 (o retrato antigo não vale mais).</li>
 *   <li>Usuário com senha provisória só acessa {@code /api/me/**} e {@code /api/auth/**}
 *       até trocar a senha; o resto da API responde 403. Vale para sessão e HTTP Basic.</li>
 * </ul>
 *
 * <p>Registrado só na cadeia {@code /api/**}, depois da autenticação Basic.</p>
 */
public class SessaoUsuarioFilter extends OncePerRequestFilter {

    private final UsuarioRepository usuarioRepo;

    public SessaoUsuarioFilter(UsuarioRepository usuarioRepo) {
        this.usuarioRepo = usuarioRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado principal) {
            Usuario retrato = principal.getUsuario();
            Usuario atual = retrato.getId() == null ? null : usuarioRepo.findById(retrato.getId()).orElse(null);

            boolean revogado = atual == null
                    || !atual.isAtivo()
                    || atual.getPerfil() != retrato.getPerfil()
                    || atual.isPodeAbastecer() != retrato.isPodeAbastecer()
                    || !Objects.equals(atual.getSenhaHash(), retrato.getSenhaHash());
            if (revogado) {
                SecurityContextHolder.clearContext();
                HttpSession sessao = request.getSession(false);
                if (sessao != null) sessao.invalidate();
                responder(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "Sua sessão foi encerrada porque o acesso foi alterado. Entre novamente.");
                return;
            }

            if (atual.isSenhaProvisoria() && !liberadoComSenhaProvisoria(caminho(request))) {
                responder(response, HttpServletResponse.SC_FORBIDDEN,
                        "Troque a senha provisória antes de usar o sistema.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static String caminho(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }

    private static boolean liberadoComSenhaProvisoria(String caminho) {
        return caminho.equals("/api/me") || caminho.startsWith("/api/me/") || caminho.startsWith("/api/auth/");
    }

    private static void responder(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"mensagem\":\"" + mensagem + "\"}");
    }
}

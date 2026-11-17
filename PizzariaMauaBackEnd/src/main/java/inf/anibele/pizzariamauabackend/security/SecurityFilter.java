package inf.anibele.pizzariamauabackend.security;

import inf.anibele.pizzariamauabackend.repository.UsuarioRepository;
import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UsuarioRepository repository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Um token antigo não deve impedir a obtenção de um novo pelo login público.
        return "POST".equals(request.getMethod())
                && (request.getContextPath() + "/auth/login").equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var tokenJWT = recuperarToken(request);

        if (tokenJWT != null) {
            try {
                // 1. Tenta extrair o "subject" (username) do token
                var subject = tokenService.getSubject(tokenJWT);

                // 2. Busca o usuário de forma segura sem estourar exceção imediata
                var usuarioOpt = repository.findByUsername(subject);

                if (usuarioOpt.isPresent()) {
                    var usuario = usuarioOpt.get();

                    // 3. Cria a autenticação do Spring injetando as Authorities (Roles) do usuário
                    var authentication = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());

                    // 4. Diz ao Spring Security: "Este usuário está autenticado e tem essas permissões"
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    SecurityContextHolder.clearContext();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            } catch (JWTVerificationException e) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        // Continua o fluxo normal da requisição (seja ela aceita ou barrada pelo Spring mais adiante)
        filterChain.doFilter(request, response);
    }

    private String recuperarToken(HttpServletRequest request) {
        var authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null) {
            return authorizationHeader.startsWith("Bearer ") ? authorizationHeader.substring(7) : "";
        }
        return null;
    }
}

package inf.anibele.pizzariamauabackend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import inf.anibele.pizzariamauabackend.model.RoleName;
import inf.anibele.pizzariamauabackend.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

@Service
public class TokenService {

    private final Algorithm algorithm;
    private final com.auth0.jwt.interfaces.JWTVerifier verifier;
    private final Duration validadeEquipe;
    private final Duration validadeMesa;
    private final Clock clock;

    @Autowired
    public TokenService(@Value("${api.security.token.secret:}") String secret,
                        @Value("${api.security.token.validade-equipe:PT8H}") String validadeEquipe,
                        @Value("${api.security.token.validade-mesa:P7D}") String validadeMesa) {
        this(secret, validadeEquipe, validadeMesa, Clock.systemUTC());
    }

    TokenService(String secret, String validadeEquipe, String validadeMesa, Clock clock) {
        byte[] chave;
        try {
            chave = Base64.getDecoder().decode(secret == null ? "" : secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Configure API_SECURITY_TOKEN_SECRET com 32 bytes aleatórios em Base64.");
        }
        // Comprimento e variedade detectam valores triviais; a origem aleatória deve ser garantida pelo operador.
        java.util.Set<Byte> distintos = new java.util.HashSet<>();
        for (byte valor : chave) distintos.add(valor);
        if (chave.length < 32 || distintos.size() < 16) {
            throw new IllegalStateException("Configure API_SECURITY_TOKEN_SECRET com pelo menos 32 bytes aleatórios em Base64.");
        }
        this.validadeEquipe = validarDuracao(validadeEquipe);
        this.validadeMesa = validarDuracao(validadeMesa);
        this.clock = clock;
        this.algorithm = Algorithm.HMAC256(chave);
        var verification = (JWTVerifier.BaseVerification) JWT.require(algorithm)
                .withIssuer("pizzaria-maua-api")
                .withClaimPresence("sub")
                .withClaimPresence("iat")
                .withClaimPresence("exp")
                .withClaimPresence("role");
        this.verifier = verification.build(clock);
    }

    private Duration validarDuracao(String valor) {
        try {
            Duration duracao = Duration.parse(valor);
            if (duracao.isNegative() || duracao.isZero() || duracao.getNano() != 0) {
                throw new IllegalArgumentException();
            }
            // Garante que a política configurada pode ser representada por Instant/JWT.
            java.time.Instant.now().plus(duracao);
            return duracao;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Validade JWT deve ser uma duração ISO-8601 positiva em segundos inteiros.");
        }
    }

    public String gerarToken(Usuario usuario) {
        try {
            var agora = clock.instant();
            var validade = usuario.getRole() == RoleName.MESA ? validadeMesa : validadeEquipe;
            return JWT.create()
                    .withIssuer("pizzaria-maua-api")
                    .withSubject(usuario.getUsername())
                    .withClaim("role", usuario.getRole().name())
                    .withIssuedAt(agora)
                    .withExpiresAt(agora.plus(validade))
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new IllegalStateException("Erro ao gerar token JWT.");
        }
    }

    public String getSubject(String tokenJWT) {
        try {
            var token = verifier.verify(tokenJWT);
            var maiorValidade = validadeEquipe.compareTo(validadeMesa) > 0 ? validadeEquipe : validadeMesa;
            if (token.getSubject() == null || token.getSubject().isBlank()
                    || token.getIssuedAtAsInstant() == null || token.getExpiresAtAsInstant() == null
                    || token.getClaim("role").asString() == null || token.getClaim("role").asString().isBlank()
                    || !token.getExpiresAtAsInstant().isAfter(token.getIssuedAtAsInstant())
                    || Duration.between(token.getIssuedAtAsInstant(), token.getExpiresAtAsInstant()).compareTo(maiorValidade) > 0) {
                throw new JWTVerificationException("Claims inválidas.");
            }
            return token.getSubject();
        } catch (JWTVerificationException | IllegalArgumentException exception) {
            throw new JWTVerificationException("Token JWT inválido ou expirado.");
        }
    }
}

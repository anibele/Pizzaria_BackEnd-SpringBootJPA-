package inf.anibele.pizzariamauabackend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-17T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final byte[] bytes = new byte[32];
    private String secret;
    private TokenService service;

    @BeforeEach void preparar() {
        new SecureRandom().nextBytes(bytes);
        secret = Base64.getEncoder().encodeToString(bytes);
        service = new TokenService(secret, "PT8H", "P7D", CLOCK);
    }
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","curto","senha_super_secreta_padrao","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
    void rejeitaSegredoInvalidoSemRevelaLo(String key) {
        var e = assertThrows(IllegalStateException.class, () -> new TokenService(key,"PT8H","P7D",CLOCK));
        if (key != null && !key.isBlank()) assertFalse(e.getMessage().contains(key));
        assertNull(e.getCause());
    }
    @ParameterizedTest @ValueSource(strings={"PT0S","-PT1H","invalido","PT0.5S","P9999999999999999999999D"})
    void rejeitaDuracoesInvalidas(String duration) {
        assertThrows(IllegalStateException.class, () -> new TokenService(secret,duration,"P7D",CLOCK));
        assertThrows(IllegalStateException.class, () -> new TokenService(secret,"PT8H",duration,CLOCK));
    }
    @ParameterizedTest @EnumSource(RoleName.class)
    void emiteTokenForteComValidadeDoPerfilEfetivo(RoleName role) {
        var u=usuario(role);
        var text=service.gerarToken(u);
        var jwt=JWT.decode(text);
        assertEquals("conta",service.getSubject(text));
        assertEquals(NOW,jwt.getIssuedAtAsInstant());
        assertEquals(NOW.plus(role==RoleName.MESA?Duration.ofDays(7):Duration.ofHours(8)),jwt.getExpiresAtAsInstant());
        assertEquals(role.name(),jwt.getClaim("role").asString());
        assertEquals("pizzaria-maua-api",jwt.getIssuer());
    }
    @Test void respeitaConfiguracaoEExpiracaoNoLimiteSemEsperar() {
        var configured=new TokenService(secret,"PT30M","P2D",CLOCK);
        var token=configured.gerarToken(usuario(RoleName.GERENTE));
        assertEquals(NOW.plusSeconds(1800),JWT.decode(token).getExpiresAtAsInstant());
        assertEquals(NOW.plus(Duration.ofDays(2)),JWT.decode(configured.gerarToken(usuario(RoleName.MESA))).getExpiresAtAsInstant());
        assertEquals("conta",new TokenService(secret,"PT30M","P2D",Clock.fixed(NOW.plusSeconds(1799),ZoneOffset.UTC)).getSubject(token));
        assertThrows(JWTVerificationException.class,()->new TokenService(secret,"PT30M","P2D",Clock.fixed(NOW.plusSeconds(1800),ZoneOffset.UTC)).getSubject(token));
    }
    @Test void rejeitaAssinaturaAntigaMesmoComValidadeDeDezAnos() {
        var t=builder().withExpiresAt(NOW.plus(Duration.ofDays(3650))).sign(Algorithm.HMAC256("senha_super_secreta_padrao"));
        assertThrows(JWTVerificationException.class,()->service.getSubject(t));
    }
    @Test void rejeitaDezAnosMesmoComAssinaturaAtual() {
        var t=builder().withExpiresAt(NOW.plus(Duration.ofDays(3650))).sign(Algorithm.HMAC256(bytes));
        assertThrows(JWTVerificationException.class,()->service.getSubject(t));
    }
    @ParameterizedTest @ValueSource(strings={"assinatura","issuer","expirado","sub","exp","iat","role","sub-vazio","role-null","role-vazio","iat-futuro"})
    void rejeitaTokensInvalidos(String tipo) {
        var b=builder();
        switch(tipo) {
            case "issuer" -> b.withIssuer("outro");
            case "expirado" -> b.withExpiresAt(NOW.minusSeconds(1));
            case "sub" -> b.withSubject(null);
            case "sub-vazio" -> b.withSubject(" ");
            case "exp" -> b.withExpiresAt((Instant)null);
            case "iat" -> b.withIssuedAt((Instant)null);
            case "role" -> b.withClaim("role",(String)null);
            case "role-vazio" -> b.withClaim("role"," ");
            case "role-null" -> b.withNullClaim("role");
            case "iat-futuro" -> b.withIssuedAt(NOW.plusSeconds(10));
        }
        var t=b.sign(tipo.equals("assinatura")?Algorithm.HMAC256("outra-chave"):Algorithm.HMAC256(bytes));
        assertThrows(JWTVerificationException.class,()->service.getSubject(t));
    }
    @Test void filtroConsultaPerfilAtualNoBancoIgnorandoRoleDoToken() throws Exception {
        var repository=mock(UsuarioRepository.class);
        var u=usuario(RoleName.MESA);
        when(repository.findByUsername("conta")).thenReturn(Optional.of(u));
        var filter=filtro(repository);
        var req=new MockHttpServletRequest();req.addHeader("Authorization","Bearer "+builder().withClaim("role","GERENTE").sign(Algorithm.HMAC256(bytes)));
        filter.doFilter(req,new MockHttpServletResponse(),new MockFilterChain());
        var auth=SecurityContextHolder.getContext().getAuthentication();
        assertSame(u,auth.getPrincipal());
        assertEquals(List.of("MESA"),auth.getAuthorities().stream().map(a->a.getAuthority()).toList());
        verify(repository).findByUsername("conta");
    }
    @ParameterizedTest @ValueSource(strings={"Bearer invalido","Basic invalido","Bearer "})
    void filtroRecusaTokenInvalidoCom401SemConsultarBanco(String header) throws Exception {
        var repository=mock(UsuarioRepository.class);
        var req=new MockHttpServletRequest();req.addHeader("Authorization",header);
        var resp=new MockHttpServletResponse();var chain=new MockFilterChain();
        filtro(repository).doFilter(req,resp,chain);
        assertEquals(401,resp.getStatus());assertNull(chain.getRequest());verifyNoInteractions(repository);
    }
    @Test void loginPublicoPermiteNovoLoginMesmoComBearerAntigo() throws Exception {
        var repository=mock(UsuarioRepository.class);
        var req=new MockHttpServletRequest("POST","/auth/login");req.addHeader("Authorization","Bearer antigo");
        var response=new MockHttpServletResponse();var chain=new MockFilterChain();
        filtro(repository).doFilter(req,response,chain);
        assertNotEquals(401,response.getStatus());assertNotNull(chain.getRequest());verifyNoInteractions(repository);
    }
    @Test void filtroRecusaUsuarioInexistente() throws Exception {
        var repository=mock(UsuarioRepository.class);
        var req=new MockHttpServletRequest();req.addHeader("Authorization","Bearer "+service.gerarToken(usuario(RoleName.MESA)));
        var resp=new MockHttpServletResponse();
        filtro(repository).doFilter(req,resp,new MockFilterChain());assertEquals(401,resp.getStatus());
    }
    @Test void filtroNaoMascaraFalhaDeInfraestruturaComoTokenInvalido() {
        var repository=mock(UsuarioRepository.class);
        when(repository.findByUsername("conta")).thenThrow(new IllegalStateException("indisponivel"));
        var req=new MockHttpServletRequest();req.addHeader("Authorization","Bearer "+service.gerarToken(usuario(RoleName.MESA)));
        assertThrows(IllegalStateException.class,()->filtro(repository).doFilter(req,new MockHttpServletResponse(),new MockFilterChain()));
    }
    private SecurityFilter filtro(UsuarioRepository r) {
        var f=new SecurityFilter();ReflectionTestUtils.setField(f,"tokenService",service);ReflectionTestUtils.setField(f,"repository",r);return f;
    }
    private com.auth0.jwt.JWTCreator.Builder builder() {
        return JWT.create().withIssuer("pizzaria-maua-api").withSubject("conta").withClaim("role","MESA").withIssuedAt(NOW).withExpiresAt(NOW.plusSeconds(60));
    }
    private Usuario usuario(RoleName role) { var u=new Usuario();u.setUsername("conta");u.setRole(role);return u; }
}

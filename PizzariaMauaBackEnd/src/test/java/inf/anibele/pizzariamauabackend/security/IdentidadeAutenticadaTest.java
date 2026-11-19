package inf.anibele.pizzariamauabackend.security;

import com.auth0.jwt.JWT;
import inf.anibele.pizzariamauabackend.controller.AutenticacaoController;
import inf.anibele.pizzariamauabackend.controller.MesaController;
import inf.anibele.pizzariamauabackend.exception.ApiExceptionHandler;
import inf.anibele.pizzariamauabackend.mapper.MesaMapper;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.*;
import inf.anibele.pizzariamauabackend.service.AutenticacaoService;
import inf.anibele.pizzariamauabackend.service.MesaService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Cadeia HTTP real e JWT real; somente persistencia simulada, sem DataSource/seeds.
@SpringJUnitConfig(IdentidadeAutenticadaTest.ConfiguracaoTeste.class)
@WebAppConfiguration
@TestPropertySource(properties = "FRONTAPI=http://localhost:5176")
class IdentidadeAutenticadaTest {
    private static final Instant AGORA = Instant.parse("2026-11-19T12:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private MesaRepository mesas;
    @Autowired private TokenService tokens;
    @Autowired private ChaveTeste chave;
    @Autowired private PasswordEncoder encoder;
    private MockMvc mvc;

    @BeforeEach void preparar() {
        reset(usuarios, mesas);
        SecurityContextHolder.clearContext();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    private static Mesa mesa(int numero) {
        var mesa = new Mesa(); mesa.setId((long) numero); mesa.setNumero(numero);
        mesa.setAtivo(true); mesa.setStatus(StatusMesa.LIVRE); return mesa;
    }
    private static Usuario usuario(String nome, RoleName perfil, Mesa mesa) {
        var usuario = new Usuario(); usuario.setId(10L); usuario.setUsername(nome);
        usuario.setRole(perfil); usuario.setMesa(mesa); usuario.setSenha("hash-sintetico-nao-expor"); return usuario;
    }
    private String autenticar(Usuario usuario) {
        when(usuarios.findByUsername(usuario.getUsername())).thenReturn(Optional.of(usuario));
        return tokens.gerarToken(usuario);
    }
    private JsonNode identidade(String token) throws Exception {
        var resultado = mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        var dados = JSON.readTree(resultado.getResponse().getContentAsString());
        assertEquals(3, dados.size());
        assertTrue(dados.has("username")); assertTrue(dados.has("role")); assertTrue(dados.has("numeroMesa"));
        assertFalse(dados.has("senha")); assertFalse(dados.has("password")); assertFalse(dados.has("token"));
        assertFalse(resultado.getResponse().getContentAsString().contains("hash-sintetico-nao-expor"));
        return dados;
    }

    @ParameterizedTest @ValueSource(strings = {"clienteA", "tablet99", "mesa0001"})
    void mesaVemDaAssociacaoEJamaisDosDigitosDoUsername(String nome) throws Exception {
        var dados = identidade(autenticar(usuario(nome, RoleName.MESA, mesa(7))));
        assertEquals(nome, dados.get("username").asText());
        assertEquals("MESA", dados.get("role").asText()); assertEquals(7, dados.get("numeroMesa").asInt());
        verify(usuarios).findByUsername(nome); verifyNoMoreInteractions(usuarios); verifyNoInteractions(mesas);
    }

    @ParameterizedTest @EnumSource(value = RoleName.class, names = {"GERENTE", "COZINHA", "CAIXA"})
    void demaisPerfisRetornamNumeroMesaExplicitamenteNulo(RoleName perfil) throws Exception {
        var dados = identidade(autenticar(usuario("equipe", perfil, null)));
        assertEquals(perfil.name(), dados.get("role").asText());
        assertTrue(dados.get("numeroMesa").isNull());
    }

    @ParameterizedTest @ValueSource(strings = {"sem-mesa", "sem-id", "sem-numero"})
    void vinculoInvalidoFalhaFechadoCom403(String caso) throws Exception {
        Mesa vinculada = mesa(7);
        if (caso.equals("sem-mesa")) vinculada = null;
        else if (caso.equals("sem-id")) vinculada.setId(null);
        else vinculada.setNumero(null);
        var token = autenticar(usuario("clienteA", RoleName.MESA, vinculada));
        var resultado = mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andReturn();
        assertFalse(resultado.getResponse().getContentAsString().contains("clienteA"));
        verifyNoInteractions(mesas);
    }

    @Test void mesaInativaNaoViraFalhaDeAutenticacao() throws Exception {
        var vinculada = mesa(7); vinculada.setAtivo(false);
        assertEquals(7, identidade(autenticar(usuario("clienteA", RoleName.MESA, vinculada))).get("numeroMesa").asInt());
    }

    @Test void anonimoNaoRecebeIdentidadeERetorna401() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        verifyNoInteractions(usuarios, mesas);
    }

    @Test void entryPoint401TambemRespeitaContextPath() throws Exception {
        mvc.perform(get("/pizzaria/auth/me").contextPath("/pizzaria"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios);
    }

    @ParameterizedTest @ValueSource(strings = {"invalido", "expirado", "outro-esquema"})
    void bearerInvalidoOuExpiradoNaoChegaAoController(String caso) throws Exception {
        String cabecalho = "Bearer token-sintetico-invalido";
        if (caso.equals("expirado")) {
            var antigo = new TokenService(chave.valor(), "PT8H", "P7D",
                    Clock.fixed(AGORA.minus(Duration.ofDays(8)), ZoneOffset.UTC));
            cabecalho = "Bearer " + antigo.gerarToken(usuario("clienteA", RoleName.MESA, mesa(7)));
        } else if (caso.equals("outro-esquema")) cabecalho = "Basic valor-sintetico";
        mvc.perform(get("/auth/me").header("Authorization", cabecalho)).andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios, mesas);
    }

    @Test void usuarioRemovidoRecusaTokenAindaAssinadoCom401() throws Exception {
        var token = tokens.gerarToken(usuario("removido", RoleName.MESA, mesa(7)));
        when(usuarios.findByUsername("removido")).thenReturn(Optional.empty());
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test void mesmoTokenRefleteNovaAssociacaoNaProximaRequisicao() throws Exception {
        var antes = usuario("clienteA", RoleName.MESA, mesa(7));
        var depois = usuario("clienteA", RoleName.MESA, mesa(12));
        when(usuarios.findByUsername("clienteA")).thenReturn(Optional.of(antes), Optional.of(depois));
        var token = tokens.gerarToken(antes);
        assertEquals(7, identidade(token).get("numeroMesa").asInt());
        assertEquals(12, identidade(token).get("numeroMesa").asInt());
        verify(usuarios, times(2)).findByUsername("clienteA"); verifyNoInteractions(mesas);
    }

    @Test void perfilAtualDoBancoPrevaleceSobreRoleDoJwt() throws Exception {
        var token = tokens.gerarToken(usuario("clienteA", RoleName.MESA, mesa(7)));
        when(usuarios.findByUsername("clienteA")).thenReturn(Optional.of(usuario("clienteA", RoleName.GERENTE, null)));
        var dados = identidade(token);
        assertEquals("GERENTE", dados.get("role").asText()); assertTrue(dados.get("numeroMesa").isNull());
    }

    @Test void parametrosLivresNaoEscolhemUsuarioOuMesaDaResposta() throws Exception {
        var token = autenticar(usuario("clienteA", RoleName.MESA, mesa(7)));
        var resultado = mvc.perform(get("/auth/me").param("username", "outro").param("numeroMesa", "99")
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        var dados = JSON.readTree(resultado.getResponse().getContentAsString());
        assertEquals("clienteA", dados.get("username").asText()); assertEquals(7, dados.get("numeroMesa").asInt());
        verify(usuarios).findByUsername("clienteA"); verifyNoMoreInteractions(usuarios);
    }

    @ParameterizedTest @EnumSource(RoleName.class)
    void loginPreservaExatamenteSeusCamposEClaims(RoleName perfil) throws Exception {
        var usuario = usuario("login-sintetico", perfil, perfil == RoleName.MESA ? mesa(7) : null);
        usuario.setSenha(encoder.encode("credencial-sintetica"));
        when(usuarios.findByUsername(usuario.getUsername())).thenReturn(Optional.of(usuario));
        MvcResult resultado = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer antigo-ignorado-pelo-login")
                        .content("{\"username\":\"login-sintetico\",\"senha\":\"credencial-sintetica\"}"))
                .andExpect(status().isOk()).andReturn();
        var dados = JSON.readTree(resultado.getResponse().getContentAsString());
        assertEquals(3, dados.size()); assertTrue(dados.has("token"));
        assertEquals(usuario.getUsername(), dados.get("username").asText());
        assertEquals(perfil.name(), dados.get("role").asText());
        var token = dados.get("token").asText();
        assertEquals(usuario.getUsername(), tokens.getSubject(token));
        assertEquals(Set.of("iss", "sub", "role", "iat", "exp"), JWT.decode(token).getClaims().keySet());
    }

    @Test void conhecerIdentidadeNaoPermiteConsultarMesaDeOutroUsuario() throws Exception {
        var propria = mesa(7); var token = autenticar(usuario("clienteA", RoleName.MESA, propria));
        identidade(token);
        when(mesas.findByNumero(7)).thenReturn(Optional.of(propria));
        mvc.perform(get("/mesas/7").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/mesas/99").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        verify(mesas).findByNumero(7); verify(mesas, never()).findByNumero(99);
    }

    private record ChaveTeste(String valor) {}
    @Configuration @EnableWebMvc
    @Import({SecurityConfigurations.class, SecurityFilter.class, AutenticacaoController.class,
            AutenticacaoService.class, AutorizacaoMesa.class, MesaController.class, MesaService.class,
            MesaMapper.class, ApiExceptionHandler.class})
    static class ConfiguracaoTeste {
        @Bean UsuarioRepository usuarioRepository() { return mock(UsuarioRepository.class); }
        @Bean MesaRepository mesaRepository() { return mock(MesaRepository.class); }
        @Bean PedidoRepository pedidoRepository() { return mock(PedidoRepository.class); }
        @Bean jakarta.persistence.EntityManager entityManager() { return mock(jakarta.persistence.EntityManager.class); }
        @Bean ChaveTeste chaveTeste() {
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            return new ChaveTeste(Base64.getEncoder().encodeToString(bytes));
        }
        @Bean TokenService tokenService(ChaveTeste chave) { return new TokenService(chave.valor(), "PT8H", "P7D", RELOGIO); }
    }
}

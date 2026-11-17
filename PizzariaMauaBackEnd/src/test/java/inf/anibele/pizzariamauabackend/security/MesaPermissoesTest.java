package inf.anibele.pizzariamauabackend.security;

import inf.anibele.pizzariamauabackend.controller.MesaController;
import inf.anibele.pizzariamauabackend.exception.ApiExceptionHandler;
import inf.anibele.pizzariamauabackend.model.RoleName;
import inf.anibele.pizzariamauabackend.model.Usuario;
import inf.anibele.pizzariamauabackend.repository.UsuarioRepository;
import inf.anibele.pizzariamauabackend.service.MesaService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(MesaPermissoesTest.ConfiguracaoTeste.class)
@WebAppConfiguration
@TestPropertySource(properties = "FRONTAPI=http://localhost:5176")
class MesaPermissoesTest {

    @Autowired private WebApplicationContext context;
    @Autowired private MesaService mesaService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TokenService tokenService;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        reset(mesaService, usuarioRepository, tokenService);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    static Stream<Arguments> permissoes() {
        List<String> perfis = List.of("GERENTE", "MESA", "COZINHA", "CAIXA", "ANONIMO");
        return Stream.of(
                rota(HttpMethod.PATCH, "/mesas/1/ativacao?ativo=true", 204, "GERENTE"),
                rota(HttpMethod.PATCH, "/mesas/1/ativacao?ativo=false", 204, "GERENTE"),
                rota(HttpMethod.GET, "/mesas/status?status=LIVRE", 200, "GERENTE", "CAIXA"),
                rota(HttpMethod.GET, "/mesas/1", 200, "GERENTE", "MESA", "CAIXA"),
                rota(HttpMethod.GET, "/mesas", 200, "GERENTE", "COZINHA", "CAIXA"),
                rota(HttpMethod.PATCH, "/mesas/1/status?status=OCUPADA", 204, "GERENTE", "CAIXA"),
                rota(HttpMethod.POST, "/mesas", 201, "GERENTE"),
                rota(HttpMethod.DELETE, "/mesas/1", 204, "GERENTE")
        ).flatMap(rota -> perfis.stream().map(perfil -> Arguments.of(
                rota.metodo(), rota.url(), perfil, rota.permitidos().contains(perfil) ? rota.sucesso() : 403)));
    }

    @ParameterizedTest(name = "{0} {1} perfil={2} HTTP={3}")
    @MethodSource("permissoes")
    void aplicaRegrasReaisSemBanco(HttpMethod metodo, String url, String perfil, int esperado) throws Exception {
        var requisicao = request(metodo, url).contentType(MediaType.APPLICATION_JSON)
                .content("{\"numero\":1,\"criarUsuario\":false}");
        if (!perfil.equals("ANONIMO")) {
            Usuario usuario = new Usuario();
            usuario.setUsername(perfil);
            usuario.setRole(RoleName.valueOf(perfil));
            when(tokenService.getSubject(perfil)).thenReturn(perfil);
            when(usuarioRepository.findByUsername(perfil)).thenReturn(Optional.of(usuario));
            requisicao.header("Authorization", "Bearer " + perfil);
        }

        mvc.perform(requisicao).andExpect(status().is(esperado));

        if (esperado == 403) {
            verifyNoInteractions(mesaService);
        } else {
            assertEquals(1, mockingDetails(mesaService).getInvocations().size());
        }
    }

    private static Rota rota(HttpMethod metodo, String url, int sucesso, String... permitidos) {
        return new Rota(metodo, url, sucesso, List.of(permitidos));
    }

    private record Rota(HttpMethod metodo, String url, int sucesso, List<String> permitidos) {}

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfigurations.class, SecurityFilter.class, MesaController.class, ApiExceptionHandler.class})
    static class ConfiguracaoTeste {
        @Bean MesaService mesaService() { return mock(MesaService.class); }
        @Bean UsuarioRepository usuarioRepository() { return mock(UsuarioRepository.class); }
        @Bean jakarta.persistence.EntityManager entityManager() { return mock(jakarta.persistence.EntityManager.class); }
        @Bean TokenService tokenService() { return mock(TokenService.class); }
        @Bean UserDetailsService userDetailsService() {
            return username -> { throw new UsernameNotFoundException("Usuário de teste não encontrado"); };
        }
    }
}

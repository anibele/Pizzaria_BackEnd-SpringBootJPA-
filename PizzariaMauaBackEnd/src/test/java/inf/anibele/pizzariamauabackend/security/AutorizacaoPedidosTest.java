package inf.anibele.pizzariamauabackend.security;

import inf.anibele.pizzariamauabackend.controller.MesaController;
import inf.anibele.pizzariamauabackend.controller.PedidoController;
import inf.anibele.pizzariamauabackend.exception.ApiExceptionHandler;
import inf.anibele.pizzariamauabackend.mapper.*;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.*;
import inf.anibele.pizzariamauabackend.service.MesaService;
import inf.anibele.pizzariamauabackend.service.PedidoService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AutorizacaoPedidosTest.ConfiguracaoTeste.class)
@WebAppConfiguration
@TestPropertySource(properties = "FRONTAPI=http://localhost:5176")
class AutorizacaoPedidosTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PedidoRepository pedidos;
    @Autowired private MesaRepository mesas;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ProdutoRepository produtos;
    @Autowired private ItemPedidoRepository itens;
    @Autowired private TokenService tokens;
    private MockMvc mvc;
    private Mesa mesa1;
    private Mesa mesa2;
    private Pedido pedido1;
    private Pedido pedido2;
    private Produto produto;

    @BeforeEach
    void preparar() {
        reset(pedidos, mesas, usuarios, produtos, itens, tokens);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        inf.anibele.pizzariamauabackend.LocksTeste.configurar(context.getBean(PedidoService.class), mesas, pedidos, produtos);
        inf.anibele.pizzariamauabackend.LocksTeste.configurar(context.getBean(MesaService.class));
        mesa1 = mesa(101L, 1);
        mesa2 = mesa(102L, 2);
        pedido1 = pedido(11L, mesa1);
        pedido2 = pedido(22L, mesa2);
        produto = new Produto();
        produto.setId(1L);
        produto.setAtivo(true);
        produto.setNome("Pizza");
        produto.setPrecoBase(new BigDecimal("50.00"));
        produto.setQtdEstoque(10);
        when(tokens.getSubject(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(usuarios.findByUsername(anyString())).thenAnswer(invocation -> {
            String perfil = invocation.getArgument(0);
            Usuario usuario = new Usuario();
            usuario.setUsername(perfil);
            usuario.setRole(List.of("mesa01", "personalizada", "SEM_VINCULO").contains(perfil)
                    ? RoleName.MESA : RoleName.valueOf(perfil));
            if (perfil.equals("mesa01")) usuario.setMesa(mesa1);
            if (perfil.equals("personalizada")) usuario.setMesa(mesa2);
            return Optional.of(usuario);
        });
        when(mesas.findByNumero(anyInt())).thenAnswer(invocation -> {
            int numero = invocation.getArgument(0);
            return numero == 1 ? Optional.of(mesa1) : numero == 2 ? Optional.of(mesa2) : Optional.empty();
        });
        when(pedidos.findById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            return id == 11 ? Optional.of(pedido1) : id == 22 ? Optional.of(pedido2) : Optional.empty();
        });
        when(pedidos.findAtivosByMesaNumero(anyInt())).thenAnswer(invocation ->
                invocation.<Integer>getArgument(0) == 1 ? List.of(pedido1) : List.of(pedido2));
        when(produtos.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(produto));
        when(pedidos.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    static Stream<Arguments> acessos() {
        return Stream.of("mesa01", "personalizada", "SEM_VINCULO", "GERENTE", "CAIXA", "COZINHA", "ANONIMO")
                .flatMap(perfil -> Stream.of(1, 2).flatMap(numero -> Stream.of("CONSULTAR", "ABRIR", "ADICIONAR", "FINALIZAR", "MESA")
                        .map(operacao -> {
                            boolean mesaPropria = perfil.equals(numero == 1 ? "mesa01" : "personalizada");
                            boolean permitido = perfil.equals("GERENTE") || mesaPropria
                                    || (perfil.equals("CAIXA") && !List.of("ABRIR", "ADICIONAR").contains(operacao));
                            return Arguments.of(perfil, numero, operacao, permitido);
                        })));
    }

    @ParameterizedTest(name = "{0} mesa={1} operacao={2} permitido={3}")
    @MethodSource("acessos")
    void aplicaTitularidadeComFiltroESevicesReais(String perfil, int numero, String operacao, boolean permitido) throws Exception {
        // Abertura usa mesa livre sem conta; os demais cenarios usam conta ativa coerente.
        if (operacao.equals("ABRIR")) {
            when(pedidos.findAtivosByMesaNumero(anyInt())).thenReturn(List.of());
        } else {
            mesa1.setStatus(StatusMesa.OCUPADA);
            mesa2.setStatus(StatusMesa.OCUPADA);
        }
        long id = numero == 1 ? 11L : 22L;
        var requisicao = switch (operacao) {
            case "CONSULTAR" -> get("/pedidos/mesa/" + numero + "/aberto");
            case "ABRIR" -> post("/pedidos").content("{\"numeroMesa\":" + numero
                    + ",\"itens\":[{\"produtoId\":1,\"quantidade\":1}]}");
            case "ADICIONAR" -> put("/pedidos/" + id + "/itens").content("[{\"produtoId\":1,\"quantidade\":1}]");
            case "FINALIZAR" -> patch("/pedidos/" + id + "/finalizar?formaPagamento=PIX");
            default -> get("/mesas/" + numero);
        };
        if (!perfil.equals("ANONIMO")) requisicao.header("Authorization", "Bearer " + perfil);
        // Cabeçalhos enviados pelo cliente não concedem outro perfil ou mesa.
        requisicao.header("X-Role", "GERENTE").header("X-Mesa", numero);
        mvc.perform(requisicao.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(permitido ? (operacao.equals("ABRIR") ? 201 : 200) : 403));

        if (!permitido) {
            verify(pedidos, never()).save(any());
            verify(produtos, never()).save(any());
            verify(mesas, never()).save(any());
            verifyNoInteractions(itens);
            assertEquals(10, produto.getQtdEstoque());
            for (Pedido pedido : List.of(pedido1, pedido2)) {
                assertEquals(new BigDecimal("50.00"), pedido.getFaturamento());
                assertTrue(pedido.getItens().isEmpty());
                assertEquals(StatusPedido.ABERTO, pedido.getStatus());
                assertEquals(operacao.equals("ABRIR") ? StatusMesa.LIVRE : StatusMesa.OCUPADA, pedido.getMesa().getStatus());
            }
        }
    }

    @Test
    void ausenciaRealContinua404ParaUsuarioVinculado() throws Exception {
        when(pedidos.findAtivosByMesaNumero(1)).thenReturn(List.of());
        mvc.perform(get("/pedidos/mesa/1/aberto").header("Authorization", "Bearer mesa01"))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
        mvc.perform(patch("/pedidos/999/finalizar?formaPagamento=PIX").header("Authorization", "Bearer mesa01"))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioSemVinculoNaoRecebe404AoTentarPedidoInexistente() throws Exception {
        mvc.perform(patch("/pedidos/999/finalizar?formaPagamento=PIX").header("Authorization", "Bearer SEM_VINCULO"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidos);
    }

    @Test
    void verificacaoDoServiceTambemNegaAusenciaDePrincipal() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> context.getBean(AutorizacaoMesa.class).verificarMesa(1));
    }

    static Stream<Arguments> conflitosAdministrativos() {
        return Stream.of(
                Arguments.of("GERENTE", "/mesas/1/status?status=LIVRE"),
                Arguments.of("CAIXA", "/mesas/1/status?status=RESERVADA"),
                Arguments.of("GERENTE", "/mesas/1/status?status=MANUTENCAO"),
                Arguments.of("GERENTE", "/mesas/1/ativacao?ativo=false"));
    }

    @ParameterizedTest @MethodSource("conflitosAdministrativos")
    void administracaoComPedidoAtivoRetorna409SemAlteracao(String perfil, String rota) throws Exception {
        mesa1.setStatus(StatusMesa.OCUPADA);
        mvc.perform(patch(rota).header("Authorization", "Bearer " + perfil))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        assertEquals(StatusMesa.OCUPADA, mesa1.getStatus());assertTrue(mesa1.isAtivo());
        verify(mesas, never()).save(any());verify(pedidos, never()).save(any());verify(produtos, never()).save(any());
    }

    @Test
    void segundaContaAguardandoPagamentoRetorna409() throws Exception {
        mesa1.setStatus(StatusMesa.OCUPADA);pedido1.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        mvc.perform(post("/pedidos").header("Authorization", "Bearer mesa01").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroMesa\":1,\"itens\":[{\"produtoId\":1,\"quantidade\":1}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        assertEquals(10,produto.getQtdEstoque());assertEquals(StatusPedido.AGUARDANDO_PAGAMENTO,pedido1.getStatus());
        verify(mesas,never()).save(any());verify(pedidos,never()).save(any());verify(produtos,never()).save(any());
    }

    @Test
    void mesaIndisponivelRetorna409SemCriarPedido() throws Exception {
        mesa1.setAtivo(false);when(pedidos.findAtivosByMesaNumero(1)).thenReturn(List.of());
        mvc.perform(post("/pedidos").header("Authorization", "Bearer mesa01").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroMesa\":1,\"itens\":[{\"produtoId\":1,\"quantidade\":1}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        assertFalse(mesa1.isAtivo());assertEquals(StatusMesa.LIVRE,mesa1.getStatus());assertEquals(10,produto.getQtdEstoque());
        verify(mesas,never()).save(any());verify(pedidos,never()).save(any());verify(produtos,never()).save(any());
    }

    @Test
    void consultaDeContaAmbiguaRetorna409SemEscolherPedido() throws Exception {
        when(pedidos.findAtivosByMesaNumero(1)).thenReturn(List.of(pedido1,pedido(33L,mesa1)));
        mvc.perform(get("/pedidos/mesa/1/aberto").header("Authorization", "Bearer mesa01"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        verify(pedidos,never()).save(any());
    }

    private Mesa mesa(Long id, int numero) {
        Mesa mesa = new Mesa();
        mesa.setId(id);
        mesa.setNumero(numero);
        mesa.setStatus(StatusMesa.LIVRE);
        return mesa;
    }

    private Pedido pedido(Long id, Mesa mesa) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setMesa(mesa);
        pedido.setStatus(StatusPedido.ABERTO);
        pedido.setFaturamento(new BigDecimal("50.00"));
        pedido.setItens(new ArrayList<>());
        return pedido;
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfigurations.class, SecurityFilter.class, AutorizacaoMesa.class,
            PedidoController.class, MesaController.class, ApiExceptionHandler.class})
    static class ConfiguracaoTeste {
        @Bean jakarta.persistence.EntityManager entityManager() { return mock(jakarta.persistence.EntityManager.class); }
        @Bean PedidoRepository pedidos() { return mock(PedidoRepository.class); }
        @Bean MesaRepository mesas() { return mock(MesaRepository.class); }
        @Bean UsuarioRepository usuarios() { return mock(UsuarioRepository.class); }
        @Bean ProdutoRepository produtos() { return mock(ProdutoRepository.class); }
        @Bean ItemPedidoRepository itens() { return mock(ItemPedidoRepository.class); }
        @Bean TokenService tokens() { return mock(TokenService.class); }
        @Bean UserDetailsService userDetailsService() {
            return username -> { throw new UsernameNotFoundException("Usuário de teste não encontrado"); };
        }
        @Bean tools.jackson.databind.json.JsonMapper jsonMapper() { return tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(); }
        @Bean inf.anibele.pizzariamauabackend.repository.IdempotenciaAdicaoRepository idempotenciaRepository() { return mock(inf.anibele.pizzariamauabackend.repository.IdempotenciaAdicaoRepository.class); }
        @Bean PedidoService pedidoService(PedidoRepository pedidos, MesaRepository mesas, ProdutoRepository produtos,
                ItemPedidoRepository itens, AutorizacaoMesa autorizacao) {
            return new PedidoService(pedidos, mesas, produtos, itens, new PedidoMapper(new ItemPedidoMapper()), autorizacao);
        }
        @Bean MesaService mesaService(MesaRepository mesas, UsuarioRepository usuarios, PasswordEncoder encoder,
                AutorizacaoMesa autorizacao, PedidoRepository pedidos) {
            return new MesaService(mesas, usuarios, encoder, new MesaMapper(), autorizacao, pedidos);
        }
    }
}

package inf.anibele.pizzariamauabackend.controller;

import inf.anibele.pizzariamauabackend.exception.ApiExceptionHandler;
import inf.anibele.pizzariamauabackend.mapper.*;
import inf.anibele.pizzariamauabackend.security.AutorizacaoMesa;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.*;
import inf.anibele.pizzariamauabackend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ValidacaoFase2Test {

    private static final String PRODUTO = "{\"nome\":\"Pizza\",\"categoria\":\"PIZZAS\","
            + "\"precoBase\":50.00,\"qtdEstoque\":10,\"ativo\":true}";

    private ProdutoRepository produtos;
    private MesaRepository mesas;
    private UsuarioRepository usuarios;
    private PedidoRepository pedidos;
    private ItemPedidoRepository itens;
    private PasswordEncoder encoder;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        produtos = mock(ProdutoRepository.class);
        mesas = mock(MesaRepository.class);
        usuarios = mock(UsuarioRepository.class);
        pedidos = mock(PedidoRepository.class);
        itens = mock(ItemPedidoRepository.class);
        encoder = mock(PasswordEncoder.class);
        ProdutoService produtoService = new ProdutoService(produtos, new ProdutoMapper());
        MesaService mesaService = new MesaService(mesas, usuarios, encoder, new MesaMapper(), mock(AutorizacaoMesa.class), pedidos);
        PedidoService pedidoService = new PedidoService(pedidos, mesas, produtos, itens,
                new PedidoMapper(new ItemPedidoMapper()), mock(AutorizacaoMesa.class));
        inf.anibele.pizzariamauabackend.LocksTeste.configurar(mesaService, mesas, pedidos);
        inf.anibele.pizzariamauabackend.LocksTeste.configurar(produtoService);
        inf.anibele.pizzariamauabackend.LocksTeste.configurar(pedidoService, mesas, pedidos, produtos);
        mvc = MockMvcBuilders.standaloneSetup(new ProdutoController(produtoService),
                        new MesaController(mesaService), new PedidoController(pedidoService))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    static Stream<Arguments> produtosInvalidos() {
        return Stream.of(
                PRODUTO.replace("\"Pizza\"", "null"),
                PRODUTO.replace("\"Pizza\"", "\"\""),
                PRODUTO.replace("\"Pizza\"", "\"   \""),
                PRODUTO.replace("\"nome\":\"Pizza\",", ""),
                PRODUTO.replace("\"PIZZAS\"", "null"),
                PRODUTO.replace("\"PIZZAS\"", "\"INVALIDA\""),
                PRODUTO.replace("\"categoria\":\"PIZZAS\",", ""),
                PRODUTO.replace("50.00", "null"),
                PRODUTO.replace("50.00", "-0.01"),
                PRODUTO.replace("\"precoBase\":50.00,", ""),
                PRODUTO.replace("50.00", "\"abc\""),
                PRODUTO.replace("\"qtdEstoque\":10", "\"qtdEstoque\":null"),
                PRODUTO.replace("\"qtdEstoque\":10", "\"qtdEstoque\":-1"),
                PRODUTO.replace("\"qtdEstoque\":10,", ""),
                PRODUTO.replace("\"qtdEstoque\":10", "\"qtdEstoque\":1.5"),
                PRODUTO.replace("\"qtdEstoque\":10", "\"qtdEstoque\":2147483648")
        ).flatMap(json -> Stream.of(Arguments.of(HttpMethod.POST, "/produtos", json),
                Arguments.of(HttpMethod.PUT, "/produtos/1", json)));
    }

    @ParameterizedTest
    @MethodSource("produtosInvalidos")
    void rejeitaCadastroEEdicaoDeProdutoInvalido(HttpMethod metodo, String url, String json) throws Exception {
        mvc.perform(request(metodo, url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        verifyNoInteractions(produtos);
    }

    @Test
    void cadastraProdutoComPrecoEEstoqueZero() throws Exception {
        when(produtos.save(any(Produto.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUTO.replace("50.00", "0").replace("\"qtdEstoque\":10", "\"qtdEstoque\":0")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value("Pizza"))
                .andExpect(jsonPath("$.precoBase").value(0)).andExpect(jsonPath("$.qtdEstoque").value(0));
        verify(produtos).save(any(Produto.class));
    }

    @Test
    void putContinuaSubstituicaoCompletaComDescricaoOpcional() throws Exception {
        Produto produto = new Produto();
        produto.setId(1L);
        produto.setDescricao(new DetalhesDescricao());
        when(produtos.findById(1L)).thenReturn(Optional.of(produto));
        when(produtos.save(produto)).thenReturn(produto);
        mvc.perform(put("/produtos/1").contentType(MediaType.APPLICATION_JSON).content(PRODUTO))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Pizza"));
        assertNull(produto.getDescricao());
        assertEquals(new BigDecimal("50.00"), produto.getPrecoBase());
        assertEquals(10, produto.getQtdEstoque());
    }

    static Stream<String> mesasInvalidas() {
        return Stream.of("{\"criarUsuario\":false}", "{\"numero\":null,\"criarUsuario\":false}",
                "{\"numero\":0,\"criarUsuario\":false}", "{\"numero\":-1,\"criarUsuario\":false}",
                "{\"numero\":1.5,\"criarUsuario\":false}", "{\"numero\":2147483648,\"criarUsuario\":false}",
                "{\"numero\":1,\"criarUsuario\":true}",
                "{\"numero\":1,\"criarUsuario\":true,\"username\":\"   \",\"password\":\"senha\"}",
                "{\"numero\":1,\"criarUsuario\":true,\"username\":\"tablet\",\"password\":null}",
                "{\"numero\":1,\"criarUsuario\":true,\"username\":\"tablet\",\"password\":\"\"}",
                "{\"numero\":1,\"criarUsuario\":true,\"username\":\"tablet\",\"password\":\"   \"}");
    }

    @ParameterizedTest
    @MethodSource("mesasInvalidas")
    void rejeitaMesaOuCredencialInvalidaSemPersistir(String json) throws Exception {
        mvc.perform(post("/mesas").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        verifyNoInteractions(mesas, usuarios, encoder);
    }

    @Test
    void cadastraMesaSemExigirCredenciaisQuandoNaoSolicitadas() throws Exception {
        when(mesas.save(any(Mesa.class))).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(post("/mesas").contentType(MediaType.APPLICATION_JSON).content("{\"numero\":12,\"criarUsuario\":false}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(12))
                .andExpect(jsonPath("$.status").value("LIVRE"));
        verifyNoInteractions(usuarios, encoder);
    }

    @Test
    void cadastraMesaComCredencialCodificada() throws Exception {
        when(mesas.save(any(Mesa.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(encoder.encode("senha-teste")).thenReturn("senha-codificada");
        mvc.perform(post("/mesas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":12,\"criarUsuario\":true,\"username\":\"tablet12\",\"password\":\"senha-teste\"}"))
                .andExpect(status().isCreated());
        verify(usuarios).save(argThat(usuario -> usuario.getUsername().equals("tablet12")
                && usuario.getSenha().equals("senha-codificada") && usuario.getRole() == RoleName.MESA));
    }

    @Test
    void mesaDuplicadaRetorna409() throws Exception {
        when(mesas.findByNumero(1)).thenReturn(Optional.of(new Mesa()));
        mvc.perform(post("/mesas").contentType(MediaType.APPLICATION_JSON).content("{\"numero\":1,\"criarUsuario\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        verify(mesas, never()).save(any());
    }

    @Test
    void usernameDuplicadoRetorna409() throws Exception {
        when(usuarios.findByUsername("tablet")).thenReturn(Optional.of(new Usuario()));
        mvc.perform(post("/mesas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":12,\"criarUsuario\":true,\"username\":\"tablet\",\"password\":\"senha\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        verify(mesas, never()).save(any());
    }

    static Stream<String> pedidosInvalidos() {
        String itens = ",\"itens\":[{\"produtoId\":1,\"quantidade\":1}]}";
        return Stream.of("{\"numeroMesa\":null", "{\"numeroMesa\":0", "{\"numeroMesa\":-1",
                        "{\"numeroMesa\":1.5", "{\"numeroMesa\":2147483648")
                .map(inicio -> inicio + itens);
    }

    @ParameterizedTest
    @MethodSource("pedidosInvalidos")
    void rejeitaNumeroDeMesaInvalidoNaAbertura(String json) throws Exception {
        mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        verifyNoInteractions(pedidos, mesas, produtos, itens);
    }

    static Stream<Arguments> recursosAusentes() {
        return Stream.of(Arguments.of(HttpMethod.GET, "/produtos/9"),
                Arguments.of(HttpMethod.DELETE, "/produtos/9"),
                Arguments.of(HttpMethod.PATCH, "/produtos/9/status?ativo=true"),
                Arguments.of(HttpMethod.GET, "/mesas/9"),
                Arguments.of(HttpMethod.PATCH, "/mesas/9/ativacao?ativo=false"),
                Arguments.of(HttpMethod.PATCH, "/mesas/9/status?status=LIVRE"),
                Arguments.of(HttpMethod.DELETE, "/mesas/9"),
                Arguments.of(HttpMethod.PATCH, "/pedidos/9/finalizar?formaPagamento=PIX"),
                Arguments.of(HttpMethod.PATCH, "/pedidos/9/confirmar"),
                Arguments.of(HttpMethod.PATCH, "/pedidos/itens/9/status?status=PRONTO"));
    }

    @ParameterizedTest
    @MethodSource("recursosAusentes")
    void recursoAusenteRetorna404(HttpMethod metodo, String url) throws Exception {
        mvc.perform(request(metodo, url)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
    }

    @Test
    void ausenciaDePedidoAtivoPreserva404SemCorpo() throws Exception {
        mvc.perform(get("/pedidos/mesa/9/aberto")).andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void edicaoDeProdutoInexistenteRetorna404() throws Exception {
        mvc.perform(put("/produtos/9").contentType(MediaType.APPLICATION_JSON).content(PRODUTO))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        verify(produtos, never()).save(any());
    }

    static Stream<String> parametrosInvalidos() {
        return Stream.of("/pedidos/mesa/0/aberto", "/pedidos/mesa/-1/aberto", "/pedidos/mesa/abc/aberto",
                "/produtos/0", "/mesas/0", "/mesas/status?status=INVALIDO", "/mesas/status",
                "/pedidos/finalizados?data=nao-e-data");
    }

    @ParameterizedTest
    @MethodSource("parametrosInvalidos")
    void parametroInvalidoRetorna400(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        verifyNoInteractions(produtos, mesas, pedidos, itens);
    }

    @Test
    void conflitoDeEstadoRetorna409SemSalvar() throws Exception {
        Pedido pedido = new Pedido();
        pedido.setStatus(StatusPedido.FINALIZADO);
        pedido.setId(7L);
        Mesa mesa=new Mesa();mesa.setId(1L);mesa.setNumero(1);mesa.setStatus(StatusMesa.OCUPADA);
        pedido.setMesa(mesa);when(mesas.findByNumero(1)).thenReturn(Optional.of(mesa));
        when(pedidos.findById(7L)).thenReturn(Optional.of(pedido));
        mvc.perform(patch("/pedidos/7/finalizar?formaPagamento=PIX"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        assertEquals(StatusPedido.FINALIZADO, pedido.getStatus());
        verify(pedidos, never()).save(any());
    }

    @Test
    void estoqueInsuficienteRetorna409() throws Exception {
        Produto produto = new Produto();
        produto.setAtivo(true);
        produto.setId(1L);
        produto.setQtdEstoque(0);
        produto.setNome("Pizza");
        when(produtos.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(produto));
        Pedido pedido = new Pedido();
        pedido.setStatus(StatusPedido.ABERTO);
        pedido.setId(7L);
        Mesa mesa=new Mesa();mesa.setId(1L);mesa.setNumero(1);mesa.setStatus(StatusMesa.OCUPADA);
        pedido.setMesa(mesa);when(mesas.findByNumero(1)).thenReturn(Optional.of(mesa));
        when(pedidos.findById(7L)).thenReturn(Optional.of(pedido));
        mvc.perform(put("/pedidos/7/itens").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"produtoId\":1,\"quantidade\":1}]"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        verify(produtos, never()).save(any());
    }

    @Test
    void falhaDeRepositoryNaConsultaAtivaRetorna500SemExporDetalhes() throws Exception {
        when(pedidos.findAtivosByMesaNumero(any()))
                .thenThrow(new DataAccessResourceFailureException("SQL credencial=segredo"));
        mvc.perform(get("/pedidos/mesa/1/aberto")).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"mensagem\":\"Não foi possível concluir a operação. Tente novamente mais tarde.\"}"));
    }

    @Test
    void falhaInesperadaNaoVira400Ou404() throws Exception {
        when(produtos.findById(1L)).thenThrow(new NullPointerException("detalhe interno"));
        mvc.perform(get("/produtos/1")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagem").isNotEmpty()).andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void integridadeRetorna409SemExporSql() throws Exception {
        when(produtos.save(any())).thenThrow(new DataIntegrityViolationException("SQL segredo"));
        mvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON).content(PRODUTO))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"mensagem\":\"A operação conflita com registros existentes ou vinculados.\"}"));
    }

    @Test
    void exclusaoDeProdutoVinculadoRetornaConflito() throws Exception {
        Produto produto = new Produto();
        produto.setNome("Pizza");
        when(produtos.findById(1L)).thenReturn(Optional.of(produto));
        doThrow(new DataIntegrityViolationException("SQL interno")).when(produtos).delete(produto);
        mvc.perform(delete("/produtos/1")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("desativar")));
    }

    @Test
    void preservaFallbackExistenteNaExclusaoDeMesaSemVazarSql() throws Exception {
        Mesa mesa = new Mesa();
        when(mesas.findByNumero(1)).thenReturn(Optional.of(mesa));
        doThrow(new DataIntegrityViolationException("SQL interno")).when(mesas).flush();
        mvc.perform(delete("/mesas/1")).andExpect(status().isConflict())
                .andExpect(content().json("{\"mensagem\":\"Não é possível excluir a mesa pois existem registros vinculados.\"}"));
        assertFalse(mesa.isAtivo());
    }

    @Test
    void preservaCodigosDeExcecoesDeSegurancaResolvidasNoController() throws Exception {
        when(produtos.findById(1L)).thenThrow(new AccessDeniedException("negado"));
        mvc.perform(get("/produtos/1")).andExpect(status().isForbidden());
        when(produtos.findById(2L)).thenThrow(new BadCredentialsException("credencial"));
        mvc.perform(get("/produtos/2")).andExpect(status().isUnauthorized());
    }
}

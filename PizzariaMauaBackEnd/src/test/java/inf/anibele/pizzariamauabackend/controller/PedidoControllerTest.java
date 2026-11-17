package inf.anibele.pizzariamauabackend.controller;

import inf.anibele.pizzariamauabackend.mapper.ItemPedidoMapper;
import inf.anibele.pizzariamauabackend.security.AutorizacaoMesa;
import inf.anibele.pizzariamauabackend.exception.ApiExceptionHandler;
import inf.anibele.pizzariamauabackend.mapper.PedidoMapper;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.*;
import inf.anibele.pizzariamauabackend.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PedidoControllerTest {

    private PedidoRepository pedidoRepository;
    private MesaRepository mesaRepository;
    private ProdutoRepository produtoRepository;
    private ItemPedidoRepository itemPedidoRepository;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        pedidoRepository = mock(PedidoRepository.class);
        mesaRepository = mock(MesaRepository.class);
        produtoRepository = mock(ProdutoRepository.class);
        itemPedidoRepository = mock(ItemPedidoRepository.class);
        PedidoService service = new PedidoService(pedidoRepository, mesaRepository, produtoRepository,
                itemPedidoRepository, new PedidoMapper(new ItemPedidoMapper()), mock(AutorizacaoMesa.class));
        mvc = MockMvcBuilders.standaloneSetup(new PedidoController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    static Stream<Arguments> entradasInvalidas() {
        return Stream.of(
                "null", "[]", "[null]",
                "[{\"produtoId\":1,\"quantidade\":0}]",
                "[{\"produtoId\":1,\"quantidade\":-2}]",
                "[{\"produtoId\":1,\"quantidade\":null}]",
                "[{\"produtoId\":1}]",
                "[{\"produtoId\":null,\"quantidade\":1}]",
                "[{\"produtoId\":0,\"quantidade\":1}]",
                "[{\"produtoId\":-1,\"quantidade\":1}]",
                "[{\"produtoId\":1,\"quantidade\":1},{\"produtoId\":1,\"quantidade\":-1}]",
                "[{\"produtoId\":1,\"quantidade\":1},null]",
                "[{\"produtoId\":1,\"quantidade\":1.5}]",
                "[{\"produtoId\":1.5,\"quantidade\":1}]",
                "[{\"produtoId\":1,\"quantidade\":2147483648}]",
                "[{\"produtoId\":9223372036854775808,\"quantidade\":1}]"
        ).flatMap(itens -> Stream.of(Arguments.of(true, itens), Arguments.of(false, itens)));
    }

    @ParameterizedTest(name = "abertura={0}, itens={1}")
    @MethodSource("entradasInvalidas")
    void responde400ComMensagemSemAlterarDados(boolean abertura, String itens) throws Exception {
        MockHttpServletRequestBuilder requisicao = abertura
                ? post("/pedidos").content("{\"numeroMesa\":1,\"itens\":" + itens + "}")
                : put("/pedidos/7/itens").content(itens);

        mvc.perform(requisicao.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist());

        verifyNoInteractions(pedidoRepository, mesaRepository, produtoRepository, itemPedidoRepository);
    }

    @Test
    void produtoInexistenteRetorna400NosDoisEndpointsSemPersistirItemValido() throws Exception {
        Produto produto = produto();
        when(produtoRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(produto));
        String itens = "[{\"produtoId\":1,\"quantidade\":2},{\"produtoId\":999,\"quantidade\":1}]";

        mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroMesa\":1,\"itens\":" + itens + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Produto inativo ou não encontrado."));
        mvc.perform(put("/pedidos/7/itens").contentType(MediaType.APPLICATION_JSON).content(itens))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Produto inativo ou não encontrado."));

        assertEquals(10, produto.getQtdEstoque());
        verify(produtoRepository, never()).save(any());
        verifyNoInteractions(pedidoRepository, mesaRepository, itemPedidoRepository);
    }

    @Test
    void preservaRespostasDeSucessoNaAberturaEAdicao() throws Exception {
        Produto produto = produto();
        Mesa mesa = new Mesa();
        mesa.setNumero(1);
        Pedido pedido = new Pedido();
        pedido.setId(7L);
        pedido.setMesa(mesa);
        pedido.setStatus(StatusPedido.ABERTO);
        pedido.setFaturamento(new BigDecimal("100.00"));
        pedido.setItens(new ArrayList<>());
        when(produtoRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(produto));
        when(mesaRepository.findByNumero(1)).thenReturn(Optional.of(mesa));
        when(pedidoRepository.findById(7L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String itens = "[{\"produtoId\":1,\"quantidade\":2}]";

        mvc.perform(post("/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroMesa\":1,\"itens\":" + itens + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.faturamento").value(100.0))
                .andExpect(jsonPath("$.itens[0].quantidade").value(2));
        mvc.perform(put("/pedidos/7/itens").contentType(MediaType.APPLICATION_JSON).content(itens))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamento").value(200.0))
                .andExpect(jsonPath("$.itens[0].quantidade").value(2));

        assertEquals(6, produto.getQtdEstoque());
    }

    private Produto produto() {
        Produto produto = new Produto();
        produto.setId(1L);
        produto.setNome("Pizza");
        produto.setAtivo(true);
        produto.setPrecoBase(new BigDecimal("50.00"));
        produto.setQtdEstoque(10);
        return produto;
    }
}

package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.ItemPedidoRequestDTO;
import inf.anibele.pizzariamauabackend.dto.PedidoRequestDTO;
import inf.anibele.pizzariamauabackend.mapper.ItemPedidoMapper;
import inf.anibele.pizzariamauabackend.security.AutorizacaoMesa;
import inf.anibele.pizzariamauabackend.mapper.PedidoMapper;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private MesaRepository mesaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private ItemPedidoRepository itemPedidoRepository;

    private PedidoService service;
    private Produto produto;
    private Mesa mesa;
    private Pedido pedido;

    @BeforeEach
    void preparar() {
        service = new PedidoService(pedidoRepository, mesaRepository, produtoRepository,
                itemPedidoRepository, new PedidoMapper(new ItemPedidoMapper()), mock(AutorizacaoMesa.class));
        inf.anibele.pizzariamauabackend.LocksTeste.configurar(service, mesaRepository, pedidoRepository, produtoRepository);
        produto = new Produto();
        produto.setId(1L);
        produto.setNome("Pizza");
        produto.setAtivo(true);
        produto.setPrecoBase(new BigDecimal("50.00"));
        produto.setQtdEstoque(10);
        mesa = new Mesa();
        mesa.setNumero(1);
        mesa.setStatus(StatusMesa.LIVRE);
        lenient().when(mesaRepository.findByNumero(1)).thenReturn(Optional.of(mesa));
        pedido = new Pedido();
        pedido.setId(7L);
        pedido.setMesa(mesa);
        pedido.setStatus(StatusPedido.ABERTO);
        pedido.setFaturamento(new BigDecimal("100.00"));
        pedido.setItens(new ArrayList<>());
    }

    static Stream<List<ItemPedidoRequestDTO>> itensInvalidos() {
        ItemPedidoRequestDTO valido = new ItemPedidoRequestDTO(1L, 1);
        return Stream.of(
                null,
                List.of(),
                Arrays.asList((ItemPedidoRequestDTO) null),
                List.of(new ItemPedidoRequestDTO(1L, 0)),
                List.of(new ItemPedidoRequestDTO(1L, -2)),
                List.of(new ItemPedidoRequestDTO(1L, null)),
                List.of(new ItemPedidoRequestDTO(null, 1)),
                List.of(new ItemPedidoRequestDTO(0L, 1)),
                List.of(new ItemPedidoRequestDTO(-1L, 1)),
                List.of(valido, new ItemPedidoRequestDTO(1L, -2)),
                Arrays.asList(valido, null));
    }

    @ParameterizedTest
    @MethodSource("itensInvalidos")
    void rejeitaAberturaInvalidaAntesDeQualquerAlteracao(List<ItemPedidoRequestDTO> itens) {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.abrirPedido(new PedidoRequestDTO(1, itens, null)));

        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        assertNotNull(erro.getReason());
        verificarEstadoOriginal();
        verifyNoInteractions(pedidoRepository, mesaRepository, produtoRepository, itemPedidoRepository);
    }

    @ParameterizedTest
    @MethodSource("itensInvalidos")
    void rejeitaAdicaoInvalidaAntesDeQualquerAlteracao(List<ItemPedidoRequestDTO> itens) {
        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.adicionarItens(7L, itens));

        assertEquals(HttpStatus.BAD_REQUEST, erro.getStatusCode());
        verificarEstadoOriginal();
        verifyNoInteractions(pedidoRepository, mesaRepository, produtoRepository, itemPedidoRepository);
    }

    @Test
    void rejeitaProdutoInexistenteAposItemValidoNosDoisFluxosSemAlteracao() {
        when(produtoRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(produto));
        when(produtoRepository.findByIdAndAtivoTrue(999L)).thenReturn(Optional.empty());
        List<ItemPedidoRequestDTO> itens = List.of(
                new ItemPedidoRequestDTO(1L, 2), new ItemPedidoRequestDTO(999L, 1));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.abrirPedido(new PedidoRequestDTO(1, itens, null))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.adicionarItens(7L, itens)).getStatusCode());

        verificarEstadoOriginal();
        verify(produtoRepository, never()).save(any());
        verifyNoInteractions(pedidoRepository, mesaRepository, itemPedidoRepository);
    }

    @Test
    void abrePedidoComQuantidadePositivaPreservandoPrecoEEstoque() {
        when(produtoRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(produto));
        when(mesaRepository.findByNumero(1)).thenReturn(Optional.of(mesa));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var resposta = service.abrirPedido(new PedidoRequestDTO(1,
                List.of(new ItemPedidoRequestDTO(1L, 2)), null));

        assertEquals(8, produto.getQtdEstoque());
        assertEquals(new BigDecimal("100.00"), resposta.faturamento());
        assertEquals(2, resposta.itens().get(0).quantidade());
        assertEquals(new BigDecimal("50.00"), resposta.itens().get(0).precoUnitario());
        assertEquals(StatusPedido.ABERTO, resposta.status());
        assertEquals(StatusMesa.OCUPADA, mesa.getStatus());
        verify(produtoRepository).save(produto);
        verify(pedidoRepository).save(any(Pedido.class));
    }

    @Test
    void adicionaItensPositivosInclusiveProdutoRepetido() {
        when(produtoRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(produto));
        when(pedidoRepository.findById(7L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(pedido)).thenReturn(pedido);

        var resposta = service.adicionarItens(7L, List.of(
                new ItemPedidoRequestDTO(1L, 2), new ItemPedidoRequestDTO(1L, 1)));

        assertEquals(7, produto.getQtdEstoque());
        assertEquals(new BigDecimal("250.00"), resposta.faturamento());
        assertEquals(2, resposta.itens().size());
        assertEquals(StatusPedido.ABERTO, pedido.getStatus());
    }

    @Test
    void aceitaLimiteDoIntegerQuandoHaEstoqueSemLimiteArbitrario() {
        produto.setQtdEstoque(Integer.MAX_VALUE);
        when(produtoRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(produto));
        when(pedidoRepository.findById(7L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(pedido)).thenReturn(pedido);

        var resposta = service.adicionarItens(7L,
                List.of(new ItemPedidoRequestDTO(1L, Integer.MAX_VALUE)));

        assertEquals(0, produto.getQtdEstoque());
        assertEquals(new BigDecimal("107374182450.00"), resposta.faturamento());
    }

    @Test
    void mantemVerificacaoDeEstoqueInsuficiente() {
        when(produtoRepository.findByIdAndAtivoTrue(1L)).thenReturn(Optional.of(produto));
        when(pedidoRepository.findById(7L)).thenReturn(Optional.of(pedido));

        assertThrows(RuntimeException.class, () -> service.adicionarItens(7L,
                List.of(new ItemPedidoRequestDTO(1L, 11))));

        verificarEstadoOriginal();
        verify(produtoRepository, never()).save(any());
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void itemLegadoSemPedidoRejeitaAlteracaoSemModificarDados() {
        ItemPedido item=new ItemPedido();item.setId(1L);
        when(itemPedidoRepository.findById(1L)).thenReturn(Optional.of(item));
        assertThrows(inf.anibele.pizzariamauabackend.exception.ConflitoException.class,
                () -> service.atualizarStatusItem(1L,StatusItemPedido.PRONTO));
        assertEquals(StatusItemPedido.PENDENTE,item.getStatus());assertNull(item.getDataHoraConclusao());
        verify(itemPedidoRepository,never()).save(any());verifyNoInteractions(mesaRepository,pedidoRepository);
    }

    @Test
    void statusNuloRetorna400SemConsultarOuAlterarDados() {
        var erro=assertThrows(ResponseStatusException.class,()->service.atualizarStatusItem(1L,null));
        assertEquals(HttpStatus.BAD_REQUEST,erro.getStatusCode());verifyNoInteractions(itemPedidoRepository,mesaRepository,pedidoRepository);
    }

    private void verificarEstadoOriginal() {
        assertEquals(10, produto.getQtdEstoque());
        assertEquals(new BigDecimal("100.00"), pedido.getFaturamento());
        assertTrue(pedido.getItens().isEmpty());
        assertEquals(StatusMesa.LIVRE, mesa.getStatus());
    }
}

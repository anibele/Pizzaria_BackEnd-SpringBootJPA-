package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.ItemPedidoRequestDTO;
import inf.anibele.pizzariamauabackend.dto.PedidoRequestDTO;
import inf.anibele.pizzariamauabackend.dto.PedidoResponseDTO;
import inf.anibele.pizzariamauabackend.mapper.PedidoMapper;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.MesaRepository;
import inf.anibele.pizzariamauabackend.repository.PedidoRepository;
import inf.anibele.pizzariamauabackend.repository.ProdutoRepository;
import inf.anibele.pizzariamauabackend.repository.ItemPedidoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final MesaRepository mesaRepository;
    private final ProdutoRepository produtoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final PedidoMapper pedidoMapper;

    public PedidoService(PedidoRepository pedidoRepository,
                         MesaRepository mesaRepository,
                         ProdutoRepository produtoRepository,
                         ItemPedidoRepository itemPedidoRepository,
                         PedidoMapper pedidoMapper) {
        this.pedidoRepository = pedidoRepository;
        this.mesaRepository = mesaRepository;
        this.produtoRepository = produtoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.pedidoMapper = pedidoMapper;
    }

    // 1. Abrir um novo pedido (Cliente sentou e pediu os primeiros itens)
    @Transactional
    public PedidoResponseDTO abrirPedido(PedidoRequestDTO dto) {
        Mesa mesa = mesaRepository.findByNumero(dto.numeroMesa())
                .orElseThrow(() -> new RuntimeException("Mesa não encontrada: " + dto.numeroMesa()));

        List<Pedido> pedidosAbertos = pedidoRepository.findByMesaNumeroAndStatus(mesa.getNumero(), StatusPedido.ABERTO);
        if (!pedidosAbertos.isEmpty()) {
            throw new RuntimeException("A mesa " + mesa.getNumero() + " já possui um pedido em aberto. Adicione itens ao pedido existente.");
        }

        Pedido pedido = new Pedido();
        pedido.setMesa(mesa);
        pedido.setDataHora(LocalDateTime.now());
        pedido.setStatus(StatusPedido.ABERTO);
        pedido.setItens(new ArrayList<>());

        BigDecimal faturamentoTotal = BigDecimal.ZERO;

        for (ItemPedidoRequestDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findByIdAndAtivoTrue(itemDto.produtoId())
                    .orElseThrow(() -> new RuntimeException("Produto inativo ou não encontrado. ID: " + itemDto.produtoId()));

            if (produto.getQtdEstoque() < itemDto.quantidade()) {
                throw new RuntimeException("Estoque insuficiente para o produto: " + produto.getNome());
            }

            produto.setQtdEstoque(produto.getQtdEstoque() - itemDto.quantidade());
            produtoRepository.save(produto);

            ItemPedido novoItem = new ItemPedido();
            novoItem.setProduto(produto);
            novoItem.setPedido(pedido);
            novoItem.setQuantidade(itemDto.quantidade());
            novoItem.setPrecoUnitario(produto.getPrecoBase());
            novoItem.setStatus(StatusItemPedido.PENDENTE);

            BigDecimal subTotal = produto.getPrecoBase().multiply(BigDecimal.valueOf(itemDto.quantidade()));
            faturamentoTotal = faturamentoTotal.add(subTotal);

            pedido.getItens().add(novoItem);
        }

        pedido.setFaturamento(faturamentoTotal);

        mesa.setStatus(StatusMesa.OCUPADA);
        mesaRepository.save(mesa);

        pedido = pedidoRepository.save(pedido);

        return pedidoMapper.toResponseDTO(pedido);
    }

    // 2. Adicionar mais itens a um pedido já aberto
    @Transactional
    public PedidoResponseDTO adicionarItens(Long pedidoId, List<ItemPedidoRequestDTO> novosItensDto) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado: " + pedidoId));

        if (pedido.getStatus() != StatusPedido.ABERTO) {
            throw new RuntimeException("Apenas pedidos ABERTOS podem receber novos itens.");
        }

        BigDecimal faturamentoAdicional = BigDecimal.ZERO;

        for (ItemPedidoRequestDTO itemDto : novosItensDto) {
            Produto produto = produtoRepository.findByIdAndAtivoTrue(itemDto.produtoId())
                    .orElseThrow(() -> new RuntimeException("Produto inativo ou não encontrado. ID: " + itemDto.produtoId()));

            if (produto.getQtdEstoque() < itemDto.quantidade()) {
                throw new RuntimeException("Estoque insuficiente para o produto: " + produto.getNome());
            }

            produto.setQtdEstoque(produto.getQtdEstoque() - itemDto.quantidade());
            produtoRepository.save(produto);

            ItemPedido novoItem = new ItemPedido();
            novoItem.setProduto(produto);
            novoItem.setPedido(pedido);
            novoItem.setQuantidade(itemDto.quantidade());
            novoItem.setPrecoUnitario(produto.getPrecoBase());
            novoItem.setStatus(StatusItemPedido.PENDENTE);

            BigDecimal subTotal = produto.getPrecoBase().multiply(BigDecimal.valueOf(itemDto.quantidade()));
            faturamentoAdicional = faturamentoAdicional.add(subTotal);

            pedido.getItens().add(novoItem);
        }

        pedido.setFaturamento(pedido.getFaturamento().add(faturamentoAdicional));
        pedido = pedidoRepository.save(pedido);

        return pedidoMapper.toResponseDTO(pedido);
    }

    // 2.5. Atualizar Status de um Item Específico na Cozinha
    @Transactional
    public void atualizarStatusItem(Long itemId, StatusItemPedido novoStatus) {
        ItemPedido item = itemPedidoRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item do pedido não encontrado. ID: " + itemId));

        item.setStatus(novoStatus);
        itemPedidoRepository.save(item);
    }

    // 3. Solicitação de Fechamento pelo Cliente (Área do Cliente - Encerramento no Tablet)
    @Transactional
    public PedidoResponseDTO finalizarPedido(Long pedidoId, String formaPagamento) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado: " + pedidoId));

        if (pedido.getStatus() != StatusPedido.ABERTO) {
            throw new RuntimeException("Apenas pedidos ABERTOS podem solicitar fechamento. Status atual: " + pedido.getStatus());
        }

        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        pedido.setFormaPagamento(formaPagamento);

        // CORREÇÃO: Altera o status da mesa de volta para LIVRE no banco de dados imediatamente
        Mesa mesa = pedido.getMesa();
        mesa.setStatus(StatusMesa.LIVRE);
        mesaRepository.save(mesa);

        pedido = pedidoRepository.save(pedido);
        return pedidoMapper.toResponseDTO(pedido);
    }

    // NOVO 3.5. Confirmar Recebimento no Caixa (Área do Gerente/Caixa - Encerra de Verdade)
    @Transactional
    public PedidoResponseDTO confirmarPagamentoEFecharMesa(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RuntimeException("Pedido não encontrado: " + pedidoId));

        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new RuntimeException("Este pedido não está aguardando pagamento. Status atual: " + pedido.getStatus());
        }

        pedido.setStatus(StatusPedido.FINALIZADO);

        Mesa mesa = pedido.getMesa();
        mesa.setStatus(StatusMesa.LIVRE);
        mesaRepository.save(mesa);

        pedido = pedidoRepository.save(pedido);
        return pedidoMapper.toResponseDTO(pedido);
    }

    // 4. Listar Pedidos da Cozinha (Tanto ABERTO quanto AGUARDANDO_PAGAMENTO)
    public List<PedidoResponseDTO> listarPedidosCozinha() {
        return pedidoRepository.findByStatusIn(List.of(StatusPedido.ABERTO, StatusPedido.AGUARDANDO_PAGAMENTO)).stream()
                .map(pedidoMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    // 5. Buscar pedido aberto de uma mesa específica
    public PedidoResponseDTO buscarPedidoAbertoDaMesa(Integer numeroMesa) {
        List<Pedido> pedidos = pedidoRepository.findByMesaNumeroAndStatus(numeroMesa, StatusPedido.ABERTO);
        if (pedidos.isEmpty()) {
            throw new RuntimeException("Nenhum pedido em aberto para a mesa " + numeroMesa);
        }
        return pedidoMapper.toResponseDTO(pedidos.get(0));
    }
}
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
import inf.anibele.pizzariamauabackend.security.AutorizacaoMesa;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import inf.anibele.pizzariamauabackend.exception.RecursoNaoEncontradoException;
import inf.anibele.pizzariamauabackend.exception.ConflitoException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PedidoService {

    @Autowired
    private EntityManager entityManager;

    private final PedidoRepository pedidoRepository;
    private final MesaRepository mesaRepository;
    private final ProdutoRepository produtoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final PedidoMapper pedidoMapper;
    private final AutorizacaoMesa autorizacaoMesa;

    public PedidoService(PedidoRepository pedidoRepository,
                         MesaRepository mesaRepository,
                         ProdutoRepository produtoRepository,
                         ItemPedidoRepository itemPedidoRepository,
                         PedidoMapper pedidoMapper, AutorizacaoMesa autorizacaoMesa) {
        this.pedidoRepository = pedidoRepository;
        this.mesaRepository = mesaRepository;
        this.produtoRepository = produtoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.pedidoMapper = pedidoMapper;
        this.autorizacaoMesa = autorizacaoMesa;
    }

    // 1. Abrir um novo pedido (Cliente sentou e pediu os primeiros itens)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PedidoResponseDTO abrirPedido(PedidoRequestDTO dto) {
        autorizacaoMesa.verificarMesa(dto.numeroMesa());
        Map<Long, Produto> produtos = validarItens(dto.itens());
        Mesa mesa = mesaRepository.findByNumeroForUpdate(dto.numeroMesa())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mesa não encontrada: " + dto.numeroMesa()));

        entityManager.detach(mesa);
        mesa = mesaRepository.findByNumeroForUpdate(mesa.getNumero())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mesa nao encontrada."));
        List<Pedido> pedidosAtivos = pedidoRepository.findAtivosByMesaNumero(mesa.getNumero());
        if (!pedidosAtivos.isEmpty()) {
            throw new ConflitoException("A mesa já possui um pedido ativo.");
        }
        if (!mesa.isAtivo() || mesa.getStatus() != StatusMesa.LIVRE) {
            throw new ConflitoException("A abertura exige uma mesa ativa e LIVRE. Revise a disponibilidade da mesa.");
        }

        bloquearProdutos(produtos);

        Pedido pedido = new Pedido();
        pedido.setMesa(mesa);
        pedido.setDataHora(LocalDateTime.now());
        pedido.setStatus(StatusPedido.ABERTO);
        pedido.setItens(new ArrayList<>());

        BigDecimal faturamentoTotal = BigDecimal.ZERO;

        for (ItemPedidoRequestDTO itemDto : dto.itens()) {
            Produto produto = produtos.get(itemDto.produtoId());

            if (produto.getQtdEstoque() < itemDto.quantidade()) {
                throw new ConflitoException("Estoque insuficiente para o produto: " + produto.getNome());
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PedidoResponseDTO adicionarItens(Long pedidoId, List<ItemPedidoRequestDTO> novosItensDto) {
        autorizacaoMesa.verificarVinculo();
        Map<Long, Produto> produtos = validarItens(novosItensDto);
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado: " + pedidoId));

        autorizacaoMesa.verificarMesa(pedido.getMesa());
        pedido = bloquearPedido(pedido);

        if (pedido.getStatus() != StatusPedido.ABERTO) {
            throw new ConflitoException("Apenas pedidos ABERTOS podem receber novos itens.");
        }

        bloquearProdutos(produtos);
        BigDecimal faturamentoAdicional = BigDecimal.ZERO;

        for (ItemPedidoRequestDTO itemDto : novosItensDto) {
            Produto produto = produtos.get(itemDto.produtoId());

            if (produto.getQtdEstoque() < itemDto.quantidade()) {
                throw new ConflitoException("Estoque insuficiente para o produto: " + produto.getNome());
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

    private Map<Long, Produto> validarItens(List<ItemPedidoRequestDTO> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos um item no pedido.");
        }

        for (ItemPedidoRequestDTO item : itens) {
            if (item == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A lista de itens não pode conter item nulo.");
            }
            if (item.produtoId() == null || item.produtoId() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um identificador de produto válido.");
            }
            if (item.quantidade() == null || item.quantidade() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A quantidade deve ser um número inteiro maior que zero.");
            }
        }

        Map<Long, Produto> produtos = new HashMap<>();
        for (ItemPedidoRequestDTO item : itens) {
            if (!produtos.containsKey(item.produtoId())) {
                Produto produto = produtoRepository.findByIdAndAtivoTrue(item.produtoId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inativo ou não encontrado."));
                produtos.put(item.produtoId(), produto);
            }
        }
        return produtos;
    }

    // 2.5. Atualizar Status de um Item Específico na Cozinha
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void atualizarStatusItem(Long itemId, StatusItemPedido novoStatus) {
        if (novoStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um status de item valido.");
        }
        ItemPedido item = itemPedidoRepository.findById(itemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item do pedido nao encontrado. ID: " + itemId));
        if (item.getPedido() == null) {
            throw new ConflitoException("Item sem pedido associado. Revise a inconsistencia.");
        }
        Pedido pedido = bloquearPedido(item.getPedido());
        entityManager.detach(item);
        item = itemPedidoRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item nao encontrado."));
        if (item.getPedido() == null || !pedido.getId().equals(item.getPedido().getId())) {
            throw new ConflitoException("O vinculo do item com o pedido esta inconsistente.");
        }
        if (pedido.getStatus() != StatusPedido.ABERTO && pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new ConflitoException("Pedidos encerrados nao permitem alterar o preparo dos itens.");
        }
        StatusItemPedido atual = item.getStatus();
        if (atual == novoStatus) return;

        // O frontend atual conclui itens diretamente de PENDENTE para PRONTO.
        boolean permitida = (atual == StatusItemPedido.PENDENTE
                && (novoStatus == StatusItemPedido.EM_PREPARO || novoStatus == StatusItemPedido.PRONTO))
                || (atual == StatusItemPedido.EM_PREPARO && novoStatus == StatusItemPedido.PRONTO);
        if (!permitida) {
            throw new ConflitoException("Transicao de status do item nao permitida.");
        }
        if (item.getDataHoraConclusao() != null) {
            throw new ConflitoException("Item nao concluido possui data de conclusao. Revise o historico.");
        }
        item.setStatus(novoStatus);
        if (novoStatus == StatusItemPedido.PRONTO) {
            item.setDataHoraConclusao(LocalDateTime.now());
        }
        itemPedidoRepository.save(item);
    }

    // 3. Solicitação de Fechamento pelo Cliente (Área do Cliente - Encerramento no Tablet)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PedidoResponseDTO finalizarPedido(Long pedidoId, String formaPagamento) {
        autorizacaoMesa.verificarVinculo();
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado: " + pedidoId));

        autorizacaoMesa.verificarMesa(pedido.getMesa());
        pedido = bloquearPedido(pedido);

        if (pedido.getStatus() != StatusPedido.ABERTO) {
            throw new ConflitoException("Apenas pedidos ABERTOS podem solicitar fechamento. Status atual: " + pedido.getStatus());
        }

        validarPedidoAtivoUnico(pedido);

        // Muda apenas o pedido para AGUARDANDO_PAGAMENTO. A mesa continua OCUPADA!
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        pedido.setFormaPagamento(formaPagamento);

        pedido = pedidoRepository.save(pedido);
        return pedidoMapper.toResponseDTO(pedido);
    }

    // NOVO 3.5. Confirmar Recebimento no Caixa (Área do Gerente/Caixa - Encerra de Verdade)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PedidoResponseDTO confirmarPagamentoEFecharMesa(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado: " + pedidoId));

        pedido = bloquearPedido(pedido);

        if (pedido.getStatus() != StatusPedido.AGUARDANDO_PAGAMENTO) {
            throw new ConflitoException("Este pedido não está aguardando pagamento. Status atual: " + pedido.getStatus());
        }

        validarPedidoAtivoUnico(pedido);

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

    // 5. Buscar pedido ativo de uma mesa específica
    public PedidoResponseDTO buscarPedidoAbertoDaMesa(Integer numeroMesa) {
        autorizacaoMesa.verificarMesa(numeroMesa);
        List<Pedido> pedidos = pedidoRepository.findAtivosByMesaNumero(numeroMesa);
        if (pedidos.isEmpty()) {
            throw new RecursoNaoEncontradoException("Nenhum pedido em aberto para a mesa " + numeroMesa);
        }
        if (pedidos.size() > 1) {
            throw new ConflitoException("A mesa possui múltiplos pedidos ativos. Revise a inconsistência antes de continuar.");
        }
        validarMesaOcupada(pedidos.get(0).getMesa());
        return pedidoMapper.toResponseDTO(pedidos.get(0));
    }

    private void validarPedidoAtivoUnico(Pedido pedido) {
        List<Pedido> ativos = pedidoRepository.findAtivosByMesaNumero(pedido.getMesa().getNumero());
        if (ativos.size() != 1 || !pedido.getId().equals(ativos.get(0).getId())) {
            throw new ConflitoException("A conta ativa da mesa está inconsistente. Revise os pedidos antes de continuar.");
        }
        validarMesaOcupada(pedido.getMesa());
    }

    private Pedido bloquearPedido(Pedido pedido) {
        Mesa mesa = mesaRepository.findByNumeroForUpdate(pedido.getMesa().getNumero())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mesa nao encontrada."));
        entityManager.detach(mesa);
        mesa = mesaRepository.findByNumeroForUpdate(mesa.getNumero())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mesa nao encontrada."));
        entityManager.detach(pedido);
        Pedido atualizado = pedidoRepository.findByIdForUpdate(pedido.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido nao encontrado."));
        // O filtro/leituras anteriores podem ter carregado entidades antes de esperar pelo lock.
        return atualizado;
    }

    private void bloquearProdutos(Map<Long, Produto> produtos) {
        for (Long id : produtos.keySet().stream().sorted().toList()) {
            entityManager.detach(produtos.get(id));
            Produto produto = produtoRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inativo ou nao encontrado."));
            if (!produto.isAtivo()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Produto inativo ou nao encontrado.");
            }
            produtos.put(id, produto);
        }
    }

    private void validarMesaOcupada(Mesa mesa) {
        if (!mesa.isAtivo() || mesa.getStatus() != StatusMesa.OCUPADA) {
            throw new ConflitoException("A mesa de um pedido ativo deve estar ativa e OCUPADA. Revise a inconsistência.");
        }
    }

    // 6. Listar Pedidos Concluídos/Finalizados de uma data específica (Área do Arquivo do Caixa)
    public List<PedidoResponseDTO> listarPedidosFinalizadosPorData(LocalDate data) {
        LocalDateTime inicioDia = data.atStartOfDay();
        LocalDateTime fimDia = data.atTime(LocalTime.MAX);

        return pedidoRepository.findByStatusAndDataHoraBetween(StatusPedido.FINALIZADO, inicioDia, fimDia).stream()
                .map(pedidoMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    // 7. Obter a soma do faturamento total de pedidos concluídos em uma data específica
    public BigDecimal obterFaturamentoPorData(LocalDate data) {
        LocalDateTime inicioDia = data.atStartOfDay();
        LocalDateTime fimDia = data.atTime(LocalTime.MAX);

        return pedidoRepository.sumFaturamentoFinalizadoByDataHoraBetween(inicioDia, fimDia);
    }

}

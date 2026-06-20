package inf.anibele.pizzariamauabackend.mapper;

import inf.anibele.pizzariamauabackend.dto.ItemPedidoResponseDTO;
import inf.anibele.pizzariamauabackend.dto.PedidoResponseDTO;
import inf.anibele.pizzariamauabackend.model.Pedido;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class PedidoMapper {

    private final ItemPedidoMapper itemPedidoMapper;

    // Injeção de dependência do sub-mapper via construtor
    public PedidoMapper(ItemPedidoMapper itemPedidoMapper) {
        this.itemPedidoMapper = itemPedidoMapper;
    }

    public PedidoResponseDTO toResponseDTO(Pedido pedido) {
        if (pedido == null) return null;

        // Converte a lista de entidades ItemPedido para DTOs
        List<ItemPedidoResponseDTO> itensDTO = (pedido.getItens() == null) ? List.of() :
                pedido.getItens().stream()
                        .map(itemPedidoMapper::toResponseDTO)
                        .collect(Collectors.toList());

        return new PedidoResponseDTO(
                pedido.getId(),
                pedido.getMesa().getNumero(),
                pedido.getDataHora(),
                pedido.getFormaPagamento(),
                pedido.getFaturamento(),
                pedido.getStatus(),
                itensDTO
        );
    }
}
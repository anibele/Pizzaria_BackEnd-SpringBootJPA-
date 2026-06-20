package inf.anibele.pizzariamauabackend.mapper;

import inf.anibele.pizzariamauabackend.dto.ItemPedidoResponseDTO;
import inf.anibele.pizzariamauabackend.model.ItemPedido;
import org.springframework.stereotype.Component;

@Component
public class ItemPedidoMapper {

    public ItemPedidoResponseDTO toResponseDTO(ItemPedido item) {
        if (item == null) return null;

        return new ItemPedidoResponseDTO(
                item.getId(),
                item.getProduto().getId(),
                item.getProduto().getNome(),
                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getStatus()
        );
    }
}
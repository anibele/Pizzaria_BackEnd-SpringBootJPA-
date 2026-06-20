package inf.anibele.pizzariamauabackend.dto;

import java.util.List;

public record PedidoRequestDTO(
        Integer numeroMesa,
        List<ItemPedidoRequestDTO> itens,
        String formaPagamento
) {}
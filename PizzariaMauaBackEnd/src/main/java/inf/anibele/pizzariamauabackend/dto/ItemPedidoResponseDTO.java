package inf.anibele.pizzariamauabackend.dto;

import inf.anibele.pizzariamauabackend.model.StatusItemPedido;

import java.math.BigDecimal;

public record ItemPedidoResponseDTO(
        Long id,
        Long produtoId,
        String produtoNome,
        Integer quantidade,
        BigDecimal precoUnitario,
        StatusItemPedido status
) {}
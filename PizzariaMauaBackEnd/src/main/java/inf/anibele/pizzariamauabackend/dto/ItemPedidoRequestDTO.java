package inf.anibele.pizzariamauabackend.dto;

public record ItemPedidoRequestDTO(
        Long produtoId,
        Integer quantidade
) {}
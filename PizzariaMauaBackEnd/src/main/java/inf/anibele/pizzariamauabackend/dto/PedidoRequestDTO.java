package inf.anibele.pizzariamauabackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

public record PedidoRequestDTO(
        @NotNull(message = "O número da mesa é obrigatório.")
        @Positive(message = "O número da mesa deve ser positivo.")
        @JsonDeserialize(converter = ItemPedidoRequestDTO.QuantidadeInteira.class) Integer numeroMesa,
        List<ItemPedidoRequestDTO> itens,
        String formaPagamento
) {}

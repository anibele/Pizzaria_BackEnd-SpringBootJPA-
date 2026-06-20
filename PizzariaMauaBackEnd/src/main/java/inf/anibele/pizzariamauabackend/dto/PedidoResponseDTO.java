package inf.anibele.pizzariamauabackend.dto;

import inf.anibele.pizzariamauabackend.model.StatusPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponseDTO(
        Long id,
        Integer numeroMesa,
        LocalDateTime dataHora,
        String formaPagamento,
        BigDecimal faturamento,
        StatusPedido status,
        List<ItemPedidoResponseDTO> itens
) {}
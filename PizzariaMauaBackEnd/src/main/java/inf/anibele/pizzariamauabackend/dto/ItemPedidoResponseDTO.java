package inf.anibele.pizzariamauabackend.dto;

import inf.anibele.pizzariamauabackend.model.StatusItemPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ItemPedidoResponseDTO(
        Long id,
        Long produtoId,
        String produtoNome,
        Integer quantidade,
        BigDecimal precoUnitario,
        StatusItemPedido status,
        LocalDateTime dataHoraInclusao, // Enviando a hora do envio
        Integer tempoPreparoMinutos,
        LocalDateTime dataHoraConclusao
) {}
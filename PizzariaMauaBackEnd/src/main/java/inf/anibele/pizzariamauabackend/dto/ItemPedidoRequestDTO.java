package inf.anibele.pizzariamauabackend.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.util.StdConverter;

import java.math.BigDecimal;

public record ItemPedidoRequestDTO(
        @JsonDeserialize(converter = IdentificadorInteiro.class)
        Long produtoId,
        @JsonDeserialize(converter = QuantidadeInteira.class)
        Integer quantidade
) {
    public static class IdentificadorInteiro extends StdConverter<BigDecimal, Long> {
        @Override
        public Long convert(BigDecimal valor) {
            return valor.longValueExact();
        }
    }

    public static class QuantidadeInteira extends StdConverter<BigDecimal, Integer> {
        @Override
        public Integer convert(BigDecimal valor) {
            return valor.intValueExact();
        }
    }
}

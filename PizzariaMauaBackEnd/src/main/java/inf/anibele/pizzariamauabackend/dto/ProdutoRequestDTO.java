package inf.anibele.pizzariamauabackend.dto;

import inf.anibele.pizzariamauabackend.model.Categoria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;

public record ProdutoRequestDTO(
        @NotBlank(message = "O nome do produto é obrigatório.") String nome,
        @Valid DetalhesDescricaoDTO descricao,
        @NotNull(message = "A categoria é obrigatória.") Categoria categoria,
        @NotNull(message = "O preço é obrigatório.")
        @PositiveOrZero(message = "O preço não pode ser negativo.")
        @Digits(integer = 36, fraction = 2, message = "Informe um preço com no máximo duas casas decimais e dentro do limite monetário.") BigDecimal precoBase,
        String imagemUrl,
        boolean ativo,
        @NotNull(message = "O estoque é obrigatório.")
        @PositiveOrZero(message = "O estoque não pode ser negativo.")
        @JsonDeserialize(converter = ItemPedidoRequestDTO.QuantidadeInteira.class) Integer qtdEstoque
) {}

package inf.anibele.pizzariamauabackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

public record MesaRequestDTO(
        @NotNull(message = "O número da mesa é obrigatório.")
        @Positive(message = "O número da mesa deve ser positivo.")
        @JsonDeserialize(converter = ItemPedidoRequestDTO.QuantidadeInteira.class) Integer numero,
        boolean criarUsuario,
        String username,
        String password
) {
    @JsonIgnore
    @AssertTrue(message = "Informe username e senha não vazios para criar a credencial da mesa.")
    public boolean isCredenciaisValidas() {
        return !criarUsuario || (username != null && !username.isBlank()
                && password != null && !password.isBlank());
    }
}

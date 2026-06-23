package inf.anibele.pizzariamauabackend.dto;

public record MesaRequestDTO(
        Integer numero,
        boolean criarUsuario,
        String username,
        String password
) {}
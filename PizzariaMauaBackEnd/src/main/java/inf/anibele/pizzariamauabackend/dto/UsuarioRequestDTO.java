package inf.anibele.pizzariamauabackend.dto;

import inf.anibele.pizzariamauabackend.model.RoleName;

public record UsuarioRequestDTO(
        String username,
        String senha,
        RoleName role
) {}
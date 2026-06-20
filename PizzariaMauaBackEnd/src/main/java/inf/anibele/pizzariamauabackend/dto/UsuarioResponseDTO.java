package inf.anibele.pizzariamauabackend.dto;

import inf.anibele.pizzariamauabackend.model.RoleName;

public record UsuarioResponseDTO(
        Long id,
        String username,
        RoleName role
) {}
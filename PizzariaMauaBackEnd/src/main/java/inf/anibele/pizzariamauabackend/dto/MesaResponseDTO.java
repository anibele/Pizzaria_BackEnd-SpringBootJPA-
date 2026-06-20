package inf.anibele.pizzariamauabackend.dto;

import inf.anibele.pizzariamauabackend.model.StatusMesa;

public record MesaResponseDTO(
        Long id,
        Integer numero,
        StatusMesa status
) {}
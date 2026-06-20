package inf.anibele.pizzariamauabackend.dto;

import java.util.List;

public record DetalhesDescricaoDTO(
        String breveDescricao,
        String tempoMedioPreparo,
        boolean pratoVegano,
        List<String> ingredientes
) {}
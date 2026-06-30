package inf.anibele.pizzariamauabackend.dto.dashboarddto;

// 5. Ranking de Gargalos da Cozinha
public record GargaloCozinhaDTO(
        String produto,
        Long vezesAtrasado,
        Long tempoMedioReal
) {
}

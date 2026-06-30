package inf.anibele.pizzariamauabackend.dto.dashboarddto;

// DTO Agregador para os Rankings
public record DashboardRankingsDTO(
        java.util.List<ProdutoCampeaoDTO> topPizzas,
        java.util.List<GargaloCozinhaDTO> gargalos
) {
}

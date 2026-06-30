package inf.anibele.pizzariamauabackend.dto.dashboarddto;

import java.math.BigDecimal;

// 1. Resumo do Topo
public record DashboardResumoDTO(
        BigDecimal faturamento,
        BigDecimal ticketMedio,
        Long tempoMedioCozinhaMinutos,
        Double taxaBebidas
) {
}

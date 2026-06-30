package inf.anibele.pizzariamauabackend.controller;

import inf.anibele.pizzariamauabackend.dto.dashboarddto.DashboardRankingsDTO;
import inf.anibele.pizzariamauabackend.dto.dashboarddto.DashboardResumoDTO;
import inf.anibele.pizzariamauabackend.dto.dashboarddto.PagamentoDTO;
import inf.anibele.pizzariamauabackend.dto.dashboarddto.VendasPorHoraDTO;
import inf.anibele.pizzariamauabackend.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import java.util.List;


@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/resumo")
    public ResponseEntity<DashboardResumoDTO> getResumo() {
        return ResponseEntity.ok(dashboardService.obterResumoDoDia());
    }

    @GetMapping("/vendas-hora")
    public ResponseEntity<List<VendasPorHoraDTO>> getVendasPorHora() {
        return ResponseEntity.ok(dashboardService.obterPicosDeDemanda());
    }

    @GetMapping("/pagamentos")
    public ResponseEntity<List<PagamentoDTO>> getPagamentos() {
        return ResponseEntity.ok(dashboardService.obterPagamentosDoDia());
    }

    @GetMapping("/rankings")
    public ResponseEntity<DashboardRankingsDTO> getRankings() {
        return ResponseEntity.ok(dashboardService.obterRankings());
    }
}
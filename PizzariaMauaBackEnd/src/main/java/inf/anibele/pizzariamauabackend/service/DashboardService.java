package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.dashboarddto.*;
import inf.anibele.pizzariamauabackend.repository.ItemPedidoRepository;
import inf.anibele.pizzariamauabackend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;

    public DashboardResumoDTO obterResumoDoDia() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDia = LocalDate.now().atTime(LocalTime.MAX);

        BigDecimal faturamento = pedidoRepository.calcularFaturamentoDoDia(inicioDia, fimDia);
        if (faturamento == null) faturamento = BigDecimal.ZERO;

        Long totalPedidos = pedidoRepository.contarPedidosDoDia(inicioDia, fimDia);

        // Evita divisão por zero
        BigDecimal ticketMedio = BigDecimal.ZERO;
        if (totalPedidos > 0) {
            ticketMedio = faturamento.divide(new BigDecimal(totalPedidos), 2, RoundingMode.HALF_UP);
        }

        // Cálculo da Taxa de Acoplamento de Bebidas (%)
        Long pedidosComBebida = itemPedidoRepository.contarPedidosComBebida(inicioDia, fimDia);
        Double taxaBebidas = 0.0;
        if (totalPedidos > 0) {
            taxaBebidas = ((double) pedidosComBebida / totalPedidos) * 100;
        }

        Long tempoMedioCozinha = itemPedidoRepository.calcularTempoMedioCozinhaDoDia(inicioDia, fimDia);

        return new DashboardResumoDTO(faturamento, ticketMedio, tempoMedioCozinha, taxaBebidas);
    }

    public List<VendasPorHoraDTO> obterPicosDeDemanda() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDia = LocalDate.now().atTime(LocalTime.MAX);

        List<Object[]> resultadosNativos = pedidoRepository.contarPedidosPorHoraNativo(inicioDia, fimDia);

        return resultadosNativos.stream()
                .map(obj -> new VendasPorHoraDTO(((Number) obj[0]).intValue(), ((Number) obj[1]).longValue()))
                .collect(Collectors.toList());
    }

    public List<PagamentoDTO> obterPagamentosDoDia() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDia = LocalDate.now().atTime(LocalTime.MAX);
        return pedidoRepository.agruparPorFormaPagamento(inicioDia, fimDia);
    }

    public DashboardRankingsDTO obterRankings() {
        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimMes = LocalDate.now().atTime(LocalTime.MAX);

        List<ProdutoCampeaoDTO> topPizzas = itemPedidoRepository.buscarTopProdutos(inicioMes, fimMes);

        if (topPizzas.size() > 5) topPizzas = topPizzas.subList(0, 5);

        List<Object[]> resultadosGargalos = itemPedidoRepository.buscarMaioresGargalosDaCozinhaNativo(inicioMes, fimMes);

        List<GargaloCozinhaDTO> gargalos = resultadosGargalos.stream()
                .map(obj -> new GargaloCozinhaDTO(
                        (String) obj[0],
                        ((Number) obj[1]).longValue(),
                        ((Number) obj[2]).longValue()
                ))
                .collect(Collectors.toList());

        return new DashboardRankingsDTO(topPizzas, gargalos);
    }
}
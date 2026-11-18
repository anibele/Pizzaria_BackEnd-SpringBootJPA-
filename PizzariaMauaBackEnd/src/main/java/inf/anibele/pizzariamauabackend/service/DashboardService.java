package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.dashboarddto.*;
import inf.anibele.pizzariamauabackend.repository.ItemPedidoRepository;
import inf.anibele.pizzariamauabackend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;

    private static final ZoneId FUSO_FINANCEIRO = ZoneId.of("America/Sao_Paulo");
    private final Clock relogio;

    @Autowired
    public DashboardService(PedidoRepository pedidos, ItemPedidoRepository itens) {
        this(pedidos, itens, Clock.systemUTC());
    }

    DashboardService(PedidoRepository pedidos, ItemPedidoRepository itens, Clock relogio) {
        this.pedidoRepository = pedidos;
        this.itemPedidoRepository = itens;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumoDoDia() {
        LocalDate dia = LocalDate.now(relogio.withZone(FUSO_FINANCEIRO));
        LocalDateTime inicioDia = dia.atStartOfDay();
        LocalDateTime fimDia = dia.plusDays(1).atStartOfDay();

        var resumoFinanceiro = pedidoRepository.calcularResumoFinanceiro(inicioDia, fimDia);
        BigDecimal faturamento = resumoFinanceiro.getFaturamento();
        if (faturamento == null) faturamento = BigDecimal.ZERO;

        Long totalPedidosPagos = resumoFinanceiro.getQuantidade();

        // Evita divisão por zero
        BigDecimal ticketMedio = BigDecimal.ZERO;
        if (totalPedidosPagos > 0) {
            ticketMedio = faturamento.divide(BigDecimal.valueOf(totalPedidosPagos), 2, RoundingMode.HALF_UP);
        }

        // Cálculo da Taxa de Acoplamento de Bebidas (%)
        Long totalPedidos = pedidoRepository.contarPedidosDoDia(inicioDia, fimDia);
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

    @Transactional(readOnly = true)
    public List<PagamentoDTO> obterPagamentosDoDia() {
        LocalDate dia = LocalDate.now(relogio.withZone(FUSO_FINANCEIRO));
        LocalDateTime inicioDia = dia.atStartOfDay();
        LocalDateTime fimDia = dia.plusDays(1).atStartOfDay();
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
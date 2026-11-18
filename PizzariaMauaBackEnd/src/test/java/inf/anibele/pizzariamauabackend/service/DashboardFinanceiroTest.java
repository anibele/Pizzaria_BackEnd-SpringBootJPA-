package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import inf.anibele.pizzariamauabackend.controller.DashboardController;
import inf.anibele.pizzariamauabackend.dto.dashboarddto.PagamentoDTO;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;

class DashboardFinanceiroTest {
    @ParameterizedTest @CsvSource({"0,0,0.00","20.31,2,10.16","0.01,3,0.00","100.01,3,33.34"})
    void ticketUsaContagemPagaDoMesmoResumo(String total,long pagos,String ticket) {
        var p=mock(PedidoRepository.class);var i=mock(ItemPedidoRepository.class);var resumo=mock(PedidoRepository.ResumoFinanceiro.class);
        when(p.calcularResumoFinanceiro(any(),any())).thenReturn(resumo);when(resumo.getFaturamento()).thenReturn(new BigDecimal(total));when(resumo.getQuantidade()).thenReturn(pagos);
        when(p.contarPedidosDoDia(any(),any())).thenReturn(10L);when(i.contarPedidosComBebida(any(),any())).thenReturn(2L);
        var dto=new DashboardService(p,i).obterResumoDoDia();assertEquals(0,new BigDecimal(ticket).compareTo(dto.ticketMedio()));assertEquals(20.0,dto.taxaBebidas());
        verify(p,times(1)).calcularResumoFinanceiro(any(),any());
    }
    @ParameterizedTest @CsvSource({"2027-01-01T02:59:59Z,2026-12-31","2027-01-01T03:00:00Z,2027-01-01","2026-12-01T02:59:59Z,2026-11-30","2026-12-01T03:00:00Z,2026-12-01"})
    void diaFinanceiroEmSaoPauloIndependeDaMaquina(String instante,String data) {
        var anterior=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
        try {
            var p=mock(PedidoRepository.class);var i=mock(ItemPedidoRepository.class);var resumo=mock(PedidoRepository.ResumoFinanceiro.class);
            when(p.calcularResumoFinanceiro(any(),any())).thenReturn(resumo);when(resumo.getFaturamento()).thenReturn(BigDecimal.ZERO);when(resumo.getQuantidade()).thenReturn(0L);
            when(p.contarPedidosDoDia(any(),any())).thenReturn(0L);
            var service=new DashboardService(p,i,Clock.fixed(Instant.parse(instante),ZoneOffset.UTC));service.obterResumoDoDia();service.obterPagamentosDoDia();
            var dia=LocalDate.parse(data);verify(p).calcularResumoFinanceiro(dia.atStartOfDay(),dia.plusDays(1).atStartOfDay());verify(p).agruparPorFormaPagamento(dia.atStartOfDay(),dia.plusDays(1).atStartOfDay());
        }finally{TimeZone.setDefault(anterior);}
    }
    @Test void contratoJsonPreservadoEFormaAusenteNaoViraDinheiro() throws Exception {
        var service=mock(DashboardService.class);when(service.obterResumoDoDia()).thenReturn(new inf.anibele.pizzariamauabackend.dto.dashboarddto.DashboardResumoDTO(new BigDecimal("20.30"),new BigDecimal("10.15"),0L,0.0));
        when(service.obterPagamentosDoDia()).thenReturn(List.of(new PagamentoDTO(null,1L),new PagamentoDTO("DINHEIRO",1L)));
        var mvc=MockMvcBuilders.standaloneSetup(new DashboardController(service)).build();
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isOk()).andExpect(jsonPath("$.faturamento").value(20.30)).andExpect(jsonPath("$.ticketMedio").value(10.15)).andExpect(jsonPath("$.tempoMedioCozinhaMinutos").value(0)).andExpect(jsonPath("$.taxaBebidas").value(0.0));
        mvc.perform(get("/api/dashboard/pagamentos")).andExpect(status().isOk()).andExpect(jsonPath("$[0].formaPagamento").value(nullValue())).andExpect(jsonPath("$[0].quantidade").value(1)).andExpect(jsonPath("$[1].formaPagamento").value("DINHEIRO"));
    }
}

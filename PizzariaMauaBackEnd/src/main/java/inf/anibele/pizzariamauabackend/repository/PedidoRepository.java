package inf.anibele.pizzariamauabackend.repository;

import inf.anibele.pizzariamauabackend.model.Pedido;
import inf.anibele.pizzariamauabackend.model.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import inf.anibele.pizzariamauabackend.dto.dashboarddto.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    // Busca os pedidos por uma lista de status (útil para a Cozinha ver apenas os pedidos ABERTOs e AGUARDANDO_PAGAMENTO)
    List<Pedido> findByStatusIn(List<StatusPedido> statuses);
    // Busca os pedidos por status (crucial para a Cozinha ver apenas os pedidos ABERTOs)
    List<Pedido> findByStatus(StatusPedido status);

    // Busca todos os pedidos de uma mesa específica
    List<Pedido> findByMesaNumero(Integer numeroMesa);

    // Busca pedidos de uma mesa que ainda estão em aberto
    List<Pedido> findByMesaNumeroAndStatus(Integer numeroMesa, StatusPedido status);

    // Busca pedidos de uma mesa que estão em qualquer um dos status fornecidos
    List<Pedido> findByMesaNumeroAndStatusIn(Integer numero, List<StatusPedido> status);

    // Busca todos os pedidos de um determinado status dentro de um intervalo de tempo (início ao fim do dia)
    List<Pedido> findByStatusAndDataHoraBetween(StatusPedido status, LocalDateTime start, LocalDateTime end);

    // Faz a soma do faturamento direto via banco de dados (o COALESCE garante que retorne 0 caso não haja vendas no dia)
    @Query("SELECT COALESCE(SUM(p.faturamento), 0) FROM Pedido p " +
            "WHERE p.status = inf.anibele.pizzariamauabackend.model.StatusPedido.FINALIZADO " +
            "AND p.dataHora BETWEEN :start AND :end")
    BigDecimal sumFaturamentoFinalizadoByDataHoraBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // --- MÉTricas de Resumo ---

    @Query("SELECT SUM(p.faturamento) FROM Pedido p WHERE p.dataHora BETWEEN :inicioDia AND :fimDia AND p.status != 'CANCELADO'")
    java.math.BigDecimal calcularFaturamentoDoDia(@Param("inicioDia") LocalDateTime inicioDia, @Param("fimDia") LocalDateTime fimDia);

    @Query("SELECT COUNT(p) FROM Pedido p WHERE p.dataHora BETWEEN :inicioDia AND :fimDia AND p.status != 'CANCELADO'")
    Long contarPedidosDoDia(@Param("inicioDia") LocalDateTime inicioDia, @Param("fimDia") LocalDateTime fimDia);

    // Pressupõe que você tem uma propriedade de forma de pagamento Enum ou String no Pedido
    @Query("SELECT new inf.anibele.pizzariamauabackend.dto.dashboarddto.PagamentoDTO(COALESCE(p.formaPagamento, 'DINHEIRO'), COUNT(p)) " +
            "FROM Pedido p WHERE p.dataHora BETWEEN :inicioDia AND :fimDia AND p.status != 'CANCELADO' " +
            "GROUP BY p.formaPagamento")
    List<PagamentoDTO> agruparPorFormaPagamento(@Param("inicioDia") LocalDateTime inicioDia, @Param("fimDia") LocalDateTime fimDia);

    // Query Nativa para extrair a HORA no MySQL/Postgres (Gráfico de Picos de Demanda)
    @Query(value = "SELECT EXTRACT(HOUR FROM data_hora) as hora, COUNT(*) as quantidadePedidos " +
            "FROM pedido WHERE data_hora BETWEEN :inicioDia AND :fimDia AND status != 'CANCELADO' " +
            "GROUP BY EXTRACT(HOUR FROM data_hora) ORDER BY hora ASC", nativeQuery = true)
    List<Object[]> contarPedidosPorHoraNativo(@Param("inicioDia") LocalDateTime inicioDia, @Param("fimDia") LocalDateTime fimDia);
}

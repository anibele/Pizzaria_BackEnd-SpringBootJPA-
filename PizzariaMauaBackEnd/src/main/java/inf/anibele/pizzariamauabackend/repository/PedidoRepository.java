package inf.anibele.pizzariamauabackend.repository;

import inf.anibele.pizzariamauabackend.model.Pedido;
import inf.anibele.pizzariamauabackend.model.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import inf.anibele.pizzariamauabackend.dto.dashboarddto.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Pedido m where m.id = :valor")
    Optional<Pedido> findByIdForUpdate(@Param("valor") Long valor);
    // Busca os pedidos por uma lista de status (útil para a Cozinha ver apenas os pedidos ABERTOs e AGUARDANDO_PAGAMENTO)
    List<Pedido> findByStatusIn(List<StatusPedido> statuses);
    // Busca os pedidos por status (crucial para a Cozinha ver apenas os pedidos ABERTOs)
    List<Pedido> findByStatus(StatusPedido status);

    boolean existsByMesaId(Long mesaId);

    // Busca todos os pedidos de uma mesa específica
    List<Pedido> findByMesaNumero(Integer numeroMesa);

    // Busca pedidos de uma mesa que ainda estão em aberto
    List<Pedido> findByMesaNumeroAndStatus(Integer numeroMesa, StatusPedido status);

    // Busca pedidos de uma mesa que estão em qualquer um dos status fornecidos
    List<Pedido> findByMesaNumeroAndStatusIn(Integer numero, List<StatusPedido> status);

    default List<Pedido> findAtivosByMesaNumero(Integer numero) {
        return findByMesaNumeroAndStatusIn(numero,
                List.of(StatusPedido.ABERTO, StatusPedido.AGUARDANDO_PAGAMENTO));
    }

    // O fluxo atual somente atribui FINALIZADO apos confirmar recebimento.
    // Historicos sem data conhecida ficam fora dos periodos, sem fallback para abertura.
    String RECEBIDOS_NO_PERIODO = "FROM Pedido p WHERE p.status = "
            + "inf.anibele.pizzariamauabackend.model.StatusPedido.FINALIZADO "
            + "AND p.dataPagamentoUtc >= :inicioDia AND p.dataPagamentoUtc < :fimDia";

    interface ResumoFinanceiro {
        BigDecimal getFaturamento();
        Long getQuantidade();
    }

    // Numerador e denominador do ticket pertencem ao mesmo SELECT/snapshot.
    @Query("SELECT COALESCE(SUM(p.faturamento), 0) AS faturamento, COUNT(p) AS quantidade " + RECEBIDOS_NO_PERIODO)
    ResumoFinanceiro calcularResumoFinanceiro(@Param("inicioDia") LocalDateTime inicioDia,
                                              @Param("fimDia") LocalDateTime fimDia);

    @Query("SELECT COALESCE(SUM(p.faturamento), 0) " + RECEBIDOS_NO_PERIODO)
    BigDecimal somarFaturamentoRecebidoNoPeriodo(@Param("inicioDia") LocalDateTime inicioDia,
                                                @Param("fimDia") LocalDateTime fimDia);

    @Query("SELECT p " + RECEBIDOS_NO_PERIODO)
    List<Pedido> findFinalizadosNoPeriodo(@Param("inicioDia") LocalDateTime inicioDia,
                                         @Param("fimDia") LocalDateTime fimDia);

    // Denominador operacional da taxa de bebidas: nao representa pedidos pagos.
    @Query("SELECT COUNT(p) FROM Pedido p WHERE p.dataHora >= :inicioDia AND p.dataHora < :fimDia AND p.status != 'CANCELADO'")
    Long contarPedidosDoDia(@Param("inicioDia") LocalDateTime inicioDia, @Param("fimDia") LocalDateTime fimDia);

    // Ausencia permanece NULL; nunca inferir DINHEIRO de informacao ausente.
    @Query("SELECT new inf.anibele.pizzariamauabackend.dto.dashboarddto.PagamentoDTO(p.formaPagamento, COUNT(p)) "
            + RECEBIDOS_NO_PERIODO + " GROUP BY p.formaPagamento")
    List<PagamentoDTO> agruparPorFormaPagamento(@Param("inicioDia") LocalDateTime inicioDia,
                                               @Param("fimDia") LocalDateTime fimDia);

    // Query Nativa para extrair a HORA no MySQL/Postgres (Gráfico de Picos de Demanda)
    @Query(value = "SELECT EXTRACT(HOUR FROM data_hora) as hora, COUNT(*) as quantidadePedidos " +
            "FROM pedido WHERE data_hora BETWEEN :inicioDia AND :fimDia AND status != 'CANCELADO' " +
            "GROUP BY EXTRACT(HOUR FROM data_hora) ORDER BY hora ASC", nativeQuery = true)
    List<Object[]> contarPedidosPorHoraNativo(@Param("inicioDia") LocalDateTime inicioDia, @Param("fimDia") LocalDateTime fimDia);
}

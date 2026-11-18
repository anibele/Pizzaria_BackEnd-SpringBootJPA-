package inf.anibele.pizzariamauabackend.repository;

import inf.anibele.pizzariamauabackend.dto.dashboarddto.ProdutoCampeaoDTO;
import inf.anibele.pizzariamauabackend.model.ItemPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ItemPedido m where m.id = :valor")
    Optional<ItemPedido> findByIdForUpdate(@Param("valor") Long valor);

    // Busca todos os itens que pertencem a um determinado pedido
    List<ItemPedido> findByPedidoId(Long pedidoId);

    // Traz o Top 5 produtos mais vendidos do mês atual
    @Query("SELECT new inf.anibele.pizzariamauabackend.dto.dashboarddto.ProdutoCampeaoDTO(pr.nome, SUM(ip.quantidade)) " +
            "FROM ItemPedido ip JOIN ip.produto pr JOIN ip.pedido p " +
            "WHERE p.dataHora BETWEEN :inicioMes AND :fimMes AND p.status != 'CANCELADO' " +
            "GROUP BY pr.id ORDER BY SUM(ip.quantidade) DESC")
    List<ProdutoCampeaoDTO> buscarTopProdutos(@Param("inicioMes") LocalDateTime inicioMes, @Param("fimMes") LocalDateTime fimMes);

    // Conta quantos pedidos no dia possuem pelo menos 1 item cuja categoria seja 'BEBIDA'
    @Query("SELECT COUNT(DISTINCT p.id) FROM ItemPedido ip JOIN ip.pedido p JOIN ip.produto pr " +
            "WHERE p.dataHora >= :inicioDia AND p.dataHora < :fimDia AND p.status != 'CANCELADO' " +
            "AND pr.categoria = 'BEBIDAS'")
    Long contarPedidosComBebida(@Param("inicioDia") LocalDateTime inicioDia, @Param("fimDia") LocalDateTime fimDia);

    // 1. CALCULA O TEMPO MÉDIO REAL DE COZINHA DO DIA (em minutos)
    @Query(value = "SELECT COALESCE(AVG(TIMESTAMPDIFF(MINUTE, data_hora_inclusao, data_hora_conclusao)), 0) " +
            "FROM item_pedido WHERE data_hora_inclusao >= :inicioDia AND data_hora_inclusao < :fimDia " +
            "AND data_hora_conclusao IS NOT NULL", nativeQuery = true)
    Long calcularTempoMedioCozinhaDoDia(@Param("inicioDia") LocalDateTime inicioDia, @Param("fimDia") LocalDateTime fimDia);

    // 2. BUSCA OS MAIORES GARGALOS (Os produtos que mais demoram para ficar prontos no mês)
    // Nota: Como não temos um "tempo estimado" por produto, vamos listar os que têm a maior MÉDIA de tempo de preparo
    @Query(value = "SELECT p.nome as produto, COUNT(ip.id) as vezesAtrasado, " +
            "ROUND(AVG(TIMESTAMPDIFF(MINUTE, ip.data_hora_inclusao, ip.data_hora_conclusao))) as tempoMedioReal " +
            "FROM item_pedido ip " +
            "JOIN produto p ON ip.produto_id = p.id " +
            "WHERE ip.data_hora_inclusao BETWEEN :inicioMes AND :fimMes " +
            "AND ip.data_hora_conclusao IS NOT NULL " +
            "GROUP BY p.id, p.nome " +
            "ORDER BY tempoMedioReal DESC LIMIT 5", nativeQuery = true)
    List<Object[]> buscarMaioresGargalosDaCozinhaNativo(@Param("inicioMes") LocalDateTime inicioMes, @Param("fimMes") LocalDateTime fimMes);


}
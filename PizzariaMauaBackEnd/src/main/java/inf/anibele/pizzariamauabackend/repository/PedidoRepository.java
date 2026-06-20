package inf.anibele.pizzariamauabackend.repository;

import inf.anibele.pizzariamauabackend.model.Pedido;
import inf.anibele.pizzariamauabackend.model.StatusPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
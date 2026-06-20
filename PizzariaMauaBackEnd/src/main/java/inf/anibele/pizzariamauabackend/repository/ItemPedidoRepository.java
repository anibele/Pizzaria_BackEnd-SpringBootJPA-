package inf.anibele.pizzariamauabackend.repository;

import inf.anibele.pizzariamauabackend.model.ItemPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Long> {

    // Busca todos os itens que pertencem a um determinado pedido
    List<ItemPedido> findByPedidoId(Long pedidoId);
}
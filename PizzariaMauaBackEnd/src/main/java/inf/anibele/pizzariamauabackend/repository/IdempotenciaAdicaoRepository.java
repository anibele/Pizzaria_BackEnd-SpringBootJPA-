package inf.anibele.pizzariamauabackend.repository;

import inf.anibele.pizzariamauabackend.model.IdempotenciaAdicao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface IdempotenciaAdicaoRepository extends JpaRepository<IdempotenciaAdicao, Long> {
    Optional<IdempotenciaAdicao> findByUsuarioIdAndPedidoIdAndChave(Long usuarioId, Long pedidoId, UUID chave);
}

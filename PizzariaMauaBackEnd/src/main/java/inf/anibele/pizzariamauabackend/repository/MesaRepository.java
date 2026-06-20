package inf.anibele.pizzariamauabackend.repository;

import inf.anibele.pizzariamauabackend.model.Mesa;
import inf.anibele.pizzariamauabackend.model.StatusMesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MesaRepository extends JpaRepository<Mesa, Long> {

    // Busca uma mesa pelo número específico (ex: Mesa 5)
    Optional<Mesa> findByNumero(Integer numero);

    // Lista as mesas filtrando pelo status (ex: buscar todas as mesas LIVRES ou OCUPADAS)
    List<Mesa> findByStatus(StatusMesa status);

}
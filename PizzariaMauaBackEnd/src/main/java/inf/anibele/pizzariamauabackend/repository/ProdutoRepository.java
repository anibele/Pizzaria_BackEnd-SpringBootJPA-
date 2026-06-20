package inf.anibele.pizzariamauabackend.repository;

import inf.anibele.pizzariamauabackend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // Busca apenas produtos ativos no sistema (usado na Área do Cliente/Cardápio)
    List<Produto> findByAtivoTrue();

    // Busca um produto específico apenas se estiver ativo
    Optional<Produto> findByIdAndAtivoTrue(Long id);

    // Busca todos os produtos no sistema, independente do status (usado no painel do Gerente)

    Optional<Produto> findByNome(String nome);

    List<Produto> findAllByIdIsNotNull();
}
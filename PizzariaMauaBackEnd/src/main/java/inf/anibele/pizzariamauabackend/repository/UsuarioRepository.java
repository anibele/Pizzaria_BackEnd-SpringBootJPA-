package inf.anibele.pizzariamauabackend.repository;

import inf.anibele.pizzariamauabackend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Busca o usuário pelo username (usado direto no carregamento de sessão do Spring Security)
    Optional<Usuario> findByUsername(String username);
}
package inf.anibele.pizzariamauabackend.mapper;

import inf.anibele.pizzariamauabackend.dto.UsuarioRequestDTO;
import inf.anibele.pizzariamauabackend.dto.UsuarioResponseDTO;
import inf.anibele.pizzariamauabackend.model.Usuario;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {

    // Converte a Model para o DTO de Resposta (Protegendo a senha)
    public UsuarioResponseDTO toResponseDTO(Usuario usuario) {
        if (usuario == null) return null;

        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getRole()
        );
    }

    // Converte o DTO de Requisição para a Model
    public Usuario toEntity(UsuarioRequestDTO dto) {
        if (dto == null) return null;

        Usuario usuario = new Usuario();
        usuario.setUsername(dto.username());
        usuario.setSenha(dto.senha()); // Será criptografada no Service futuramente
        usuario.setRole(dto.role());

        return usuario;
    }
}
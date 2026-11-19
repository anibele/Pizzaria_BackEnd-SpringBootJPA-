package inf.anibele.pizzariamauabackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import inf.anibele.pizzariamauabackend.model.RoleName;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UsuarioAtualDTO(String username, RoleName role, Integer numeroMesa) {}

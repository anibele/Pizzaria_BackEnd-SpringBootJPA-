package inf.anibele.pizzariamauabackend.security;

import inf.anibele.pizzariamauabackend.model.Mesa;
import inf.anibele.pizzariamauabackend.model.RoleName;
import inf.anibele.pizzariamauabackend.model.Usuario;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AutorizacaoMesa {

    public void verificarVinculo() {
        mesaAutorizada();
    }

    public void verificarMesa(Integer numero) {
        Mesa autorizada = mesaAutorizada();
        if (autorizada != null && !autorizada.getNumero().equals(numero)) {
            throw new AccessDeniedException("Acesso a outra mesa não permitido.");
        }
    }

    public void verificarMesa(Mesa mesa) {
        Mesa autorizada = mesaAutorizada();
        if (autorizada != null && (mesa == null || !autorizada.getId().equals(mesa.getId()))) {
            throw new AccessDeniedException("Acesso a pedido de outra mesa não permitido.");
        }
    }

    private Mesa mesaAutorizada() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !autenticacao.isAuthenticated()
                || !(autenticacao.getPrincipal() instanceof Usuario usuario) || usuario.getRole() == null) {
            throw new AccessDeniedException("Usuário autenticado necessário.");
        }
        if (usuario.getRole() != RoleName.MESA) return null;

        Mesa mesa = usuario.getMesa();
        if (mesa == null || mesa.getId() == null || mesa.getNumero() == null) {
            throw new AccessDeniedException("Credencial sem vínculo válido com uma mesa.");
        }
        return mesa;
    }
}

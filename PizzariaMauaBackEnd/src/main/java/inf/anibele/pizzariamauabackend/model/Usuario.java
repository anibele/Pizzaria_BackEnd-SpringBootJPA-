package inf.anibele.pizzariamauabackend.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Data
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleName role;

    @OneToOne
    @JoinColumn(name = "mesa_id", unique = true, foreignKey = @ForeignKey(name = "fk_usuario_mesa"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Mesa mesa;

    @JsonIgnore
    @AssertTrue(message = "Usuários MESA precisam de uma mesa; os demais perfis não devem ter esse vínculo.")
    public boolean isVinculoMesaValido() {
        return role != null && (role == RoleName.MESA ? mesa != null : mesa == null);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Converte o seu enum em uma permissão que o Spring entende
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @JsonIgnore
    @Override
    public String getPassword() {
        return this.senha;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    // Como é um projeto simplificado, vamos deixar todas as travas como "true" (livres)
    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}

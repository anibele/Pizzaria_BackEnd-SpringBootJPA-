package inf.anibele.pizzariamauabackend.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer numero;

    @Enumerated(EnumType.STRING)
    private StatusMesa status;
}
package inf.anibele.pizzariamauabackend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Data
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    private Integer quantidade;
    private BigDecimal precoUnitario;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusItemPedido status = StatusItemPedido.PENDENTE;

}
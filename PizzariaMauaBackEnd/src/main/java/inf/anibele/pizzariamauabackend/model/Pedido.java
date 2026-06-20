package inf.anibele.pizzariamauabackend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "mesa_id", nullable = false)
    private Mesa mesa;

    private LocalDateTime dataHora;

    private String formaPagamento;

    private BigDecimal faturamento;

    @Enumerated(EnumType.STRING)
    private StatusPedido status;

    // Relacionamento inverso para conseguirmos buscar os itens a partir do pedido, se necessário
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
    private List<ItemPedido> itens;
}
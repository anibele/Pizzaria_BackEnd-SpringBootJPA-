package inf.anibele.pizzariamauabackend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    // NOVO CAMPO: Registra o momento exato em que o item entrou na cozinha
    @Column(name = "data_hora_inclusao", nullable = false, updatable = false)
    private LocalDateTime dataHoraInclusao;

    @Column(name = "data_hora_conclusao")
    private LocalDateTime dataHoraConclusao;

    // Executado automaticamente antes do INSERT no banco
    @PrePersist
    protected void onCreate() {
        this.dataHoraInclusao = LocalDateTime.now();
    }

}
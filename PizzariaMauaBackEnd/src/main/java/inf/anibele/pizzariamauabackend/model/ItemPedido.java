package inf.anibele.pizzariamauabackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
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

    // Identidade persistida; nunca percorrer relacionamentos ou campos mutaveis.
    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof ItemPedido entity && getId() != null && getId().equals(entity.getId());
    }

    @Override
    public final int hashCode() { return ItemPedido.class.hashCode(); }

    @Override
    public final String toString() { return "ItemPedido(id=" + getId() + ")"; }
}
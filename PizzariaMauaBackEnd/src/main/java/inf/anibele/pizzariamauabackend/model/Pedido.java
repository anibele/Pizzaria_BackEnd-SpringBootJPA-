package inf.anibele.pizzariamauabackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "mesa_id", nullable = false)
    private Mesa mesa;

    private LocalDateTime dataHora;

    private String formaPagamento;

    // UTC por convencao: DATETIME nao armazena timezone. Historico desconhecido e NULL.
    @Column(name = "data_pagamento_utc")
    private LocalDateTime dataPagamentoUtc;

    private BigDecimal faturamento;

    @Enumerated(EnumType.STRING)
    private StatusPedido status;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
    private List<ItemPedido> itens;
    // Identidade persistida; nunca percorrer relacionamentos ou campos mutaveis.
    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof Pedido entity && getId() != null && getId().equals(entity.getId());
    }

    @Override
    public final int hashCode() { return Pedido.class.hashCode(); }

    @Override
    public final String toString() { return "Pedido(id=" + getId() + ")"; }
}
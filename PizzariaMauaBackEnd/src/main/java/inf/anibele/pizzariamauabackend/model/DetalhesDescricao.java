package inf.anibele.pizzariamauabackend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import lombok.Getter;

import java.util.List;

@Getter
@Embeddable
public class DetalhesDescricao {

    // Getters e Setters
    private String breveDescricao;
    private String tempoMedioPreparo;
    private boolean pratoVegano;

    @ElementCollection
    @CollectionTable(name = "produto_ingredientes", joinColumns = @JoinColumn(name = "produto_id"))
    @Column(name = "ingrediente")
    private List<String> ingredientes;

    public void setBreveDescricao(String breveDescricao) { this.breveDescricao = breveDescricao; }

    public void setTempoMedioPreparo(String tempoMedioPreparo) { this.tempoMedioPreparo = tempoMedioPreparo; }

    public void setPratoVegano(boolean pratoVegano) { this.pratoVegano = pratoVegano; }

    public void setIngredientes(List<String> ingredientes) { this.ingredientes = ingredientes; }
}
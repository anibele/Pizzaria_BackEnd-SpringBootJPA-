package inf.anibele.pizzariamauabackend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import java.util.List;

@Embeddable
public class DetalhesDescricao {

    private String breveDescricao;
    private String tempoMedioPreparo;
    private boolean pratoVegano; // Usaremos boolean nativo agora!

    @ElementCollection // Cria uma tabela auxiliar no banco só para os ingredientes associada ao produto
    @CollectionTable(name = "produto_ingredientes", joinColumns = @JoinColumn(name = "produto_id"))
    @Column(name = "ingrediente")
    private List<String> ingredientes;

    // Getters e Setters
    public String getBreveDescricao() { return breveDescricao; }
    public void setBreveDescricao(String breveDescricao) { this.breveDescricao = breveDescricao; }

    public String getTempoMedioPreparo() { return tempoMedioPreparo; }
    public void setTempoMedioPreparo(String tempoMedioPreparo) { this.tempoMedioPreparo = tempoMedioPreparo; }

    public boolean isPratoVegano() { return pratoVegano; }
    public void setPratoVegano(boolean pratoVegano) { this.pratoVegano = pratoVegano; }

    public List<String> getIngredientes() { return ingredientes; }
    public void setIngredientes(List<String> ingredientes) { this.ingredientes = ingredientes; }
}
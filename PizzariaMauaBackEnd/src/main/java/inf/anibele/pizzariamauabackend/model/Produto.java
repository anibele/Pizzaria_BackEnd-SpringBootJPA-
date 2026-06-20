package inf.anibele.pizzariamauabackend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Data
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    @Embedded
    private DetalhesDescricao descricao;
    @Enumerated(EnumType.STRING)
    private Categoria categoria;
    private BigDecimal precoBase;
    private String imagemUrl;
    private boolean ativo;
    private Integer qtdEstoque;
}
package inf.anibele.pizzariamauabackend.dto;

import inf.anibele.pizzariamauabackend.model.Categoria;
import java.math.BigDecimal;

public record ProdutoRequestDTO(
        String nome,
        DetalhesDescricaoDTO descricao,
        Categoria categoria,
        BigDecimal precoBase,
        String imagemUrl,
        boolean ativo,
        Integer qtdEstoque
) {}
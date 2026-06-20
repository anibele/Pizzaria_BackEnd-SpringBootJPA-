package inf.anibele.pizzariamauabackend.mapper;

import inf.anibele.pizzariamauabackend.dto.DetalhesDescricaoDTO;
import inf.anibele.pizzariamauabackend.dto.ProdutoRequestDTO;
import inf.anibele.pizzariamauabackend.dto.ProdutoResponseDTO;
import inf.anibele.pizzariamauabackend.model.DetalhesDescricao;
import inf.anibele.pizzariamauabackend.model.Produto;
import org.springframework.stereotype.Component;

@Component
public class ProdutoMapper {

    // Converte a Model para o DTO de Resposta (Saída da API)
    public ProdutoResponseDTO toResponseDTO(Produto produto) {
        if (produto == null) return null;

        return new ProdutoResponseDTO(
                produto.getId(),
                produto.getNome(),
                toDetalhesDTO(produto.getDescricao()),
                produto.getCategoria(),
                produto.getPrecoBase(),
                produto.getImagemUrl(),
                produto.isAtivo(),
                produto.getQtdEstoque()
        );
    }

    // Converte o DTO de Requisição para a Model (Entrada da API para persistência)
    public Produto toEntity(ProdutoRequestDTO dto) {
        if (dto == null) return null;

        Produto produto = new Produto();
        produto.setNome(dto.nome());
        produto.setDescricao(toDetalhesEntity(dto.descricao()));
        produto.setCategoria(dto.categoria());
        produto.setPrecoBase(dto.precoBase());
        produto.setImagemUrl(dto.imagemUrl());
        produto.setAtivo(dto.ativo());
        produto.setQtdEstoque(dto.qtdEstoque());

        return produto;
    }

    // Converte a Model Embutida para o DTO
    private DetalhesDescricaoDTO toDetalhesDTO(DetalhesDescricao detalhes) {
        if (detalhes == null) return null;

        return new DetalhesDescricaoDTO(
                detalhes.getBreveDescricao(),
                detalhes.getTempoMedioPreparo(),
                detalhes.isPratoVegano(),
                detalhes.getIngredientes()
        );
    }

    // Converte o DTO para a Model Embutida
    private DetalhesDescricao toDetalhesEntity(DetalhesDescricaoDTO dto) {
        if (dto == null) return null;

        DetalhesDescricao detalhes = new DetalhesDescricao();
        detalhes.setBreveDescricao(dto.breveDescricao());
        detalhes.setTempoMedioPreparo(dto.tempoMedioPreparo());
        detalhes.setPratoVegano(dto.pratoVegano());
        detalhes.setIngredientes(dto.ingredientes());

        return detalhes;
    }
}
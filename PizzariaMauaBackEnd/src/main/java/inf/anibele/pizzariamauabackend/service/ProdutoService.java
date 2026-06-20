package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.ProdutoRequestDTO;
import inf.anibele.pizzariamauabackend.dto.ProdutoResponseDTO;
import inf.anibele.pizzariamauabackend.mapper.ProdutoMapper;
import inf.anibele.pizzariamauabackend.model.DetalhesDescricao;
import inf.anibele.pizzariamauabackend.model.Produto;
import inf.anibele.pizzariamauabackend.repository.ProdutoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final ProdutoMapper produtoMapper;

    public ProdutoService(ProdutoRepository produtoRepository, ProdutoMapper produtoMapper) {
        this.produtoRepository = produtoRepository;
        this.produtoMapper = produtoMapper;
    }

    // 1. Cadastrar novo produto
    public ProdutoResponseDTO cadastrar(ProdutoRequestDTO dto) {
        Produto produto = produtoMapper.toEntity(dto);
        produto = produtoRepository.save(produto);
        return produtoMapper.toResponseDTO(produto);
    }

    // 2. Listar TODOS os produtos (Para a Área Administrativa)
    public List<ProdutoResponseDTO> listarTodos() {
        return produtoRepository.findAllByIdIsNotNull().stream()
                .map(produtoMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    // 3. Listar apenas os produtos ATIVOS (Para a Área do Cliente/Cardápio)
    public List<ProdutoResponseDTO> listarAtivos() {
        return produtoRepository.findByAtivoTrue().stream()
                .map(produtoMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    // 4. Buscar um produto específico por ID
    public ProdutoResponseDTO buscarPorId(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));
        return produtoMapper.toResponseDTO(produto);
    }

    // 5. Editar um produto existente
    public ProdutoResponseDTO editar(Long id, ProdutoRequestDTO dto) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));

        // Atualizando os dados da entidade com os dados que vieram do DTO
        produto.setNome(dto.nome());
        produto.setCategoria(dto.categoria());
        produto.setPrecoBase(dto.precoBase());
        produto.setImagemUrl(dto.imagemUrl());
        produto.setAtivo(dto.ativo());
        produto.setQtdEstoque(dto.qtdEstoque());

        // 👈 CORREÇÃO: Mapeando manualmente o subobjeto embutido para evitar erro de compilação
        if (dto.descricao() != null) {
            DetalhesDescricao detalhes = new DetalhesDescricao();
            detalhes.setBreveDescricao(dto.descricao().breveDescricao());
            detalhes.setTempoMedioPreparo(dto.descricao().tempoMedioPreparo());
            detalhes.setPratoVegano(dto.descricao().pratoVegano());
            detalhes.setIngredientes(dto.descricao().ingredientes());
            produto.setDescricao(detalhes);
        } else {
            produto.setDescricao(null);
        }

        produto = produtoRepository.save(produto);
        return produtoMapper.toResponseDTO(produto);
    }

    // 6. Ativar ou Desativar um produto (controle de exibição)
    public ProdutoResponseDTO alternarStatus(Long id, boolean ativo) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));

        produto.setAtivo(ativo);
        produto = produtoRepository.save(produto);
        return produtoMapper.toResponseDTO(produto);
    }

    // 7. Excluir produto (com verificação de integridade referencial)
    public void excluir(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado com o ID: " + id));

        try {
            produtoRepository.delete(produto);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Não é possível excluir definitivamente o produto '"
                    + produto.getNome()
                    + "' pois ele já está vinculado a pedidos anteriores no sistema. Ação recomendada: desativar o produto.");
        }
    }
}
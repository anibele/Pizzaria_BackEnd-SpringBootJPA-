package inf.anibele.pizzariamauabackend.controller;

import inf.anibele.pizzariamauabackend.dto.ProdutoRequestDTO;
import inf.anibele.pizzariamauabackend.dto.ProdutoResponseDTO;
import inf.anibele.pizzariamauabackend.service.ProdutoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/produtos")
@CrossOrigin(origins = "*")
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    // 1. Cadastrar novo produto (Área Administrativa/Gerente)
    @PostMapping
    public ResponseEntity<ProdutoResponseDTO> cadastrar(@RequestBody ProdutoRequestDTO requestDTO) {
        ProdutoResponseDTO response = produtoService.cadastrar(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. Listar TODOS os produtos (Área Administrativa/Gerente)
    @GetMapping
    public ResponseEntity<List<ProdutoResponseDTO>> listarTodos() {
        return ResponseEntity.ok(produtoService.listarTodos());
    }

    // 3. Listar apenas os ATIVOS (Área do Cliente/Cardápio no Tablet)
    @GetMapping("/ativos")
    public ResponseEntity<List<ProdutoResponseDTO>> listarAtivos() {
        return ResponseEntity.ok(produtoService.listarAtivos());
    }

    // 4. Buscar um produto específico por ID (Uso geral)
    @GetMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(produtoService.buscarPorId(id));
    }

    // 5. Editar um produto existente (Área Administrativa/Gerente)
    @PutMapping("/{id}")
    public ResponseEntity<ProdutoResponseDTO> editar(@PathVariable Long id, @RequestBody ProdutoRequestDTO requestDTO) {
        return ResponseEntity.ok(produtoService.editar(id, requestDTO));
    }

    // 6. Ativar/Desativar produto rapidamente (Área Administrativa/Gerente)
    // Usamos PatchMapping porque estamos alterando apenas um estado específico (um "remendo") e não o objeto inteiro
    @PatchMapping("/{id}/status")
    public ResponseEntity<ProdutoResponseDTO> alternarStatus(@PathVariable Long id, @RequestParam boolean ativo) {
        return ResponseEntity.ok(produtoService.alternarStatus(id, ativo));
    }

    // 7. Excluir produto (Área Administrativa/Gerente)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        produtoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
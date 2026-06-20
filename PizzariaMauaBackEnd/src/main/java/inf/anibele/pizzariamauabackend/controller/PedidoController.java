package inf.anibele.pizzariamauabackend.controller;

import inf.anibele.pizzariamauabackend.dto.ItemPedidoRequestDTO;
import inf.anibele.pizzariamauabackend.dto.PedidoRequestDTO;
import inf.anibele.pizzariamauabackend.dto.PedidoResponseDTO;
import inf.anibele.pizzariamauabackend.model.StatusItemPedido;
import inf.anibele.pizzariamauabackend.service.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pedidos")
@CrossOrigin(origins = "*")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    // 1. Abrir Novo Pedido (Área do Cliente - Primeiro envio do Tablet)
    @PostMapping
    public ResponseEntity<PedidoResponseDTO> abrirPedido(@RequestBody PedidoRequestDTO requestDTO) {
        PedidoResponseDTO response = pedidoService.abrirPedido(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. Adicionar mais itens ao pedido atual (Área do Cliente - Tablet)
    @PutMapping("/{id}/itens")
    public ResponseEntity<PedidoResponseDTO> adicionarItens(
            @PathVariable Long id,
            @RequestBody List<ItemPedidoRequestDTO> novosItens) {
        PedidoResponseDTO response = pedidoService.adicionarItens(id, novosItens);
        return ResponseEntity.ok(response);
    }

    // 2.5. Atualizar Status de um Item Individual (Área da Cozinha)
    // Exemplo de chamada: PATCH http://localhost:8080/pedidos/itens/12/status?status=PRONTO
    @PatchMapping("/itens/{itemId}/status")
    public ResponseEntity<Void> atualizarStatusItem(
            @PathVariable Long itemId,
            @RequestParam StatusItemPedido status) {
        pedidoService.atualizarStatusItem(itemId, status);
        return ResponseEntity.noContent().build();
    }

    // 3. Solicitar Fechamento do Pedido / Enviar forma de Pagamento (Área do Cliente - Tablet)
    @PatchMapping("/{id}/finalizar")
    public ResponseEntity<PedidoResponseDTO> finalizarPedido(
            @PathVariable Long id,
            @RequestParam String formaPagamento) {
        PedidoResponseDTO response = pedidoService.finalizarPedido(id, formaPagamento);
        return ResponseEntity.ok(response);
    }

    // NOVO 3.5. Confirmar Recebimento e Efetivar Fechamento da Mesa (Área do Caixa/Admin)
    // Exemplo de chamada: PATCH http://localhost:8080/pedidos/12/confirmar
    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<PedidoResponseDTO> confirmarPagamento(@PathVariable Long id) {
        PedidoResponseDTO response = pedidoService.confirmarPagamentoEFecharMesa(id);
        return ResponseEntity.ok(response);
    }

    // 4. Listar Pedidos Ativos (Área da Cozinha - Monitor de Serviço)
    @GetMapping("/cozinha")
    public ResponseEntity<List<PedidoResponseDTO>> listarPedidosCozinha() {
        return ResponseEntity.ok(pedidoService.listarPedidosCozinha());
    }

    // 5. Buscar o pedido em aberto de uma mesa (Área do Cliente - Sincronização do Tablet)
    @GetMapping("/mesa/{numero}/aberto")
    public ResponseEntity<?> buscarPedidoAbertoDaMesa(@PathVariable int numero) {
        try {
            PedidoResponseDTO pedido = pedidoService.buscarPedidoAbertoDaMesa(numero);
            return ResponseEntity.ok(pedido);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
package inf.anibele.pizzariamauabackend.controller;

import inf.anibele.pizzariamauabackend.dto.MesaRequestDTO;
import inf.anibele.pizzariamauabackend.dto.MesaResponseDTO;
import inf.anibele.pizzariamauabackend.model.StatusMesa;
import inf.anibele.pizzariamauabackend.service.MesaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mesas")
@CrossOrigin(origins = "*")
public class MesaController {

    private final MesaService mesaService;

    public MesaController(MesaService mesaService) {
        this.mesaService = mesaService;
    }

    // 1. Cadastrar nova mesa (Área Administrativa/Gerente)
    @PostMapping
    public ResponseEntity<MesaResponseDTO> cadastrar(@RequestBody MesaRequestDTO requestDTO) {
        MesaResponseDTO response = mesaService.cadastrar(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. Listar todas as mesas do salão (Visão Geral - Gerente e Cozinha)
    @GetMapping
    public ResponseEntity<List<MesaResponseDTO>> listarTodas() {
        return ResponseEntity.ok(mesaService.listarTodas());
    }

    // 3. Listar mesas filtrando por status (Ex: /mesas/status?status=LIVRE)
    @GetMapping("/status")
    public ResponseEntity<List<MesaResponseDTO>> listarPorStatus(@RequestParam StatusMesa status) {
        return ResponseEntity.ok(mesaService.listarPorStatus(status));
    }

    // 4. Buscar dados de uma mesa específica pelo número (Uso geral / Verificação do Tablet)
    @GetMapping("/{numero}")
    public ResponseEntity<MesaResponseDTO> buscarPorNumero(@PathVariable Integer numero) {
        return ResponseEntity.ok(mesaService.buscarPorNumero(numero));
    }

    // 5. Atualizar status manualmente se necessário (Gerente)
    @PatchMapping("/{numero}/status")
    public ResponseEntity<Void> atualizarStatus(@PathVariable Integer numero, @RequestParam StatusMesa status) {
        mesaService.atualizarStatus(numero, status);
        return ResponseEntity.noContent().build();
    }

    // 6. Excluir uma mesa do sistema (Área Administrativa/Gerente)
    @DeleteMapping("/{numero}")
    public ResponseEntity<Void> excluir(@PathVariable Integer numero) {
        mesaService.excluir(numero);
        return ResponseEntity.noContent().build();
    }
}
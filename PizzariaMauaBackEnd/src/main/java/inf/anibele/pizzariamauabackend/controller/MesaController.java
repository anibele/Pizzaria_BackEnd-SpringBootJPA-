package inf.anibele.pizzariamauabackend.controller;

import inf.anibele.pizzariamauabackend.dto.MesaRequestDTO;
import inf.anibele.pizzariamauabackend.dto.MesaResponseDTO;
import inf.anibele.pizzariamauabackend.model.StatusMesa;
import inf.anibele.pizzariamauabackend.service.MesaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

    @PostMapping
    public ResponseEntity<MesaResponseDTO> cadastrar(@Valid @RequestBody MesaRequestDTO requestDTO) {
        MesaResponseDTO response = mesaService.cadastrar(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MesaResponseDTO>> listarTodas() {
        return ResponseEntity.ok(mesaService.listarTodas());
    }

    @GetMapping("/status")
    public ResponseEntity<List<MesaResponseDTO>> listarPorStatus(@RequestParam StatusMesa status) {
        return ResponseEntity.ok(mesaService.listarPorStatus(status));
    }

    @GetMapping("/{numero}")
    public ResponseEntity<MesaResponseDTO> buscarPorNumero(@Positive @PathVariable Integer numero) {
        return ResponseEntity.ok(mesaService.buscarPorNumero(numero));
    }

    @PatchMapping("/{numero}/status")
    public ResponseEntity<Void> atualizarStatus(@Positive @PathVariable Integer numero, @RequestParam StatusMesa status) {
        mesaService.atualizarStatus(numero, status);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{numero}/ativacao")
    public ResponseEntity<Void> alterarAtivacao(@Positive @PathVariable Integer numero, @RequestParam boolean ativo) {
        mesaService.alterarAtivacao(numero, ativo);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{numero}")
    public ResponseEntity<Void> excluir(@Positive @PathVariable Integer numero) {
        mesaService.excluir(numero);
        return ResponseEntity.noContent().build();
    }
}

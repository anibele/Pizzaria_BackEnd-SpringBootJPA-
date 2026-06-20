package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.MesaRequestDTO;
import inf.anibele.pizzariamauabackend.dto.MesaResponseDTO;
import inf.anibele.pizzariamauabackend.mapper.MesaMapper;
import inf.anibele.pizzariamauabackend.model.Mesa;
import inf.anibele.pizzariamauabackend.model.StatusMesa;
import inf.anibele.pizzariamauabackend.repository.MesaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MesaService {

    private final MesaRepository mesaRepository;
    private final MesaMapper mesaMapper;

    public MesaService(MesaRepository mesaRepository, MesaMapper mesaMapper) {
        this.mesaRepository = mesaRepository;
        this.mesaMapper = mesaMapper;
    }

    // 1. Cadastrar nova mesa pelo Gerente
    public MesaResponseDTO cadastrar(MesaRequestDTO dto) {
        // Regra de negócio: Impede o cadastro de mesas duplicadas
        if (mesaRepository.findByNumero(dto.numero()).isPresent()) {
            throw new RuntimeException("Já existe uma mesa cadastrada com o número: " + dto.numero());
        }

        Mesa mesa = mesaMapper.toEntity(dto);
        mesa = mesaRepository.save(mesa);
        return mesaMapper.toResponseDTO(mesa);
    }

    // 2. Listar todas as mesas (Ideal para o painel de visão geral do salão)
    public List<MesaResponseDTO> listarTodas() {
        return mesaRepository.findAll().stream()
                .map(mesaMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    // 3. Listar mesas filtrando por status (Ex: front-end buscar apenas mesas LIVRES)
    public List<MesaResponseDTO> listarPorStatus(StatusMesa status) {
        return mesaRepository.findByStatus(status).stream()
                .map(mesaMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    // 4. Buscar os dados de uma mesa específica
    public MesaResponseDTO buscarPorNumero(Integer numero) {
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new RuntimeException("Mesa não encontrada com o número: " + numero));
        return mesaMapper.toResponseDTO(mesa);
    }

    // 5. Atualizar Status da Mesa (Metodo auxiliar)
    // Esse metodo não retorna DTO porque será usado internamente por outros Services (como o PedidoService)
    public void atualizarStatus(Integer numero, StatusMesa status) {
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new RuntimeException("Mesa não encontrada com o número: " + numero));

        mesa.setStatus(status);
        mesaRepository.save(mesa);
    }

    // 6. Excluir uma mesa (caso uma mesa física quebre ou seja removida do salão)
    public void excluir(Integer numero) {
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new RuntimeException("Mesa não encontrada com o número: " + numero));

        try {
            mesaRepository.delete(mesa);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Não é possível excluir a Mesa " + numero + " pois existem históricos de pedidos vinculados a ela.");
        }
    }
}
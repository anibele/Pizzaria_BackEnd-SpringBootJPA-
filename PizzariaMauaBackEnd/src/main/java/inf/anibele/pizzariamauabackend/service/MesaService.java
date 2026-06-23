package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.MesaRequestDTO;
import inf.anibele.pizzariamauabackend.dto.MesaResponseDTO;
import inf.anibele.pizzariamauabackend.mapper.MesaMapper;
import inf.anibele.pizzariamauabackend.model.Mesa;
import inf.anibele.pizzariamauabackend.model.StatusMesa;
import inf.anibele.pizzariamauabackend.model.Usuario;
import inf.anibele.pizzariamauabackend.model.RoleName;
import inf.anibele.pizzariamauabackend.repository.MesaRepository;
import inf.anibele.pizzariamauabackend.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MesaService {

    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final MesaMapper mesaMapper;

    public MesaService(MesaRepository mesaRepository, UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder, MesaMapper mesaMapper) {
        this.mesaRepository = mesaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.mesaMapper = mesaMapper;
    }

    @Transactional
    public MesaResponseDTO cadastrar(MesaRequestDTO dto) {
        if (mesaRepository.findByNumero(dto.numero()).isPresent()) {
            throw new RuntimeException("Já existe uma mesa cadastrada com o número: " + dto.numero());
        }

        if (dto.criarUsuario()) {
            if (dto.username() == null || dto.username().isBlank()) {
                throw new RuntimeException("O nome de usuário não pode ser vazio se a criação de credenciais estiver ativa.");
            }
            if (usuarioRepository.findByUsername(dto.username()).isPresent()) {
                throw new RuntimeException("O usuário '" + dto.username() + "' já está em uso no sistema.");
            }
        }

        Mesa mesa = mesaMapper.toEntity(dto);
        mesa = mesaRepository.save(mesa);

        if (dto.criarUsuario()) {
            Usuario usuarioMesa = new Usuario();
            usuarioMesa.setUsername(dto.username());
            usuarioMesa.setSenha(passwordEncoder.encode(dto.password()));
            usuarioMesa.setRole(RoleName.MESA);
            usuarioRepository.save(usuarioMesa);
        }

        return mesaMapper.toResponseDTO(mesa);
    }

    public List<MesaResponseDTO> listarTodas() {
        return mesaRepository.findAll().stream()
                .map(mesaMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public List<MesaResponseDTO> listarPorStatus(StatusMesa status) {
        return mesaRepository.findByStatus(status).stream()
                .map(mesaMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public MesaResponseDTO buscarPorNumero(Integer numero) {
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new RuntimeException("Mesa não encontrada com o número: " + numero));
        return mesaMapper.toResponseDTO(mesa);
    }

    @Transactional
    public void atualizarStatus(Integer numero, StatusMesa status) {
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new RuntimeException("Mesa não encontrada com o número: " + numero));

        mesa.setStatus(status);
        mesaRepository.save(mesa);
    }

    @Transactional
    public void alterarAtivacao(Integer numero, boolean ativo) {
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new RuntimeException("Mesa não encontrada com o número: " + numero));

        mesa.setAtivo(ativo);
        mesaRepository.save(mesa);
    }

    @Transactional
    public void excluir(Integer numero) {
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new RuntimeException("Mesa não encontrada com o número: " + numero));

        try {
            // Remove a mesa física
            mesaRepository.delete(mesa);

            // Regra de limpeza do usuário associado
            String usernameProvavel = "mesa" + numero;
            usuarioRepository.findByUsername(usernameProvavel).ifPresent(usuarioRepository::delete);

            // Força o Hibernate a executar o delete no banco AGORA para validar Constraints
            mesaRepository.flush();

        } catch (DataIntegrityViolationException e) {
            throw new DataIntegrityViolationException("Não é possível excluir a Mesa " + numero + " pois existem históricos de pedidos vinculados a ela.");
        }
    }
}
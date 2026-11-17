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
import inf.anibele.pizzariamauabackend.repository.PedidoRepository;
import inf.anibele.pizzariamauabackend.security.AutorizacaoMesa;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import inf.anibele.pizzariamauabackend.exception.RecursoNaoEncontradoException;
import inf.anibele.pizzariamauabackend.exception.ConflitoException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MesaService {

    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final MesaMapper mesaMapper;
    private final AutorizacaoMesa autorizacaoMesa;
    private final PedidoRepository pedidoRepository;

    public MesaService(MesaRepository mesaRepository, UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder, MesaMapper mesaMapper, AutorizacaoMesa autorizacaoMesa,
                       PedidoRepository pedidoRepository) {
        this.mesaRepository = mesaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.mesaMapper = mesaMapper;
        this.autorizacaoMesa = autorizacaoMesa;
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional
    public MesaResponseDTO cadastrar(MesaRequestDTO dto) {
        if (mesaRepository.findByNumero(dto.numero()).isPresent()) {
            throw new ConflitoException("Já existe uma mesa cadastrada com o número: " + dto.numero());
        }

        if (dto.criarUsuario()) {
            if (usuarioRepository.findByUsername(dto.username()).isPresent()) {
                throw new ConflitoException("O usuário '" + dto.username() + "' já está em uso no sistema.");
            }
        }

        Mesa mesa = mesaMapper.toEntity(dto);
        mesa = mesaRepository.save(mesa);

        if (dto.criarUsuario()) {
            Usuario usuarioMesa = new Usuario();
            usuarioMesa.setUsername(dto.username());
            usuarioMesa.setSenha(passwordEncoder.encode(dto.password()));
            usuarioMesa.setRole(RoleName.MESA);
            usuarioMesa.setMesa(mesa);
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
        autorizacaoMesa.verificarMesa(numero);
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mesa não encontrada com o número: " + numero));
        return mesaMapper.toResponseDTO(mesa);
    }

    @Transactional
    public void atualizarStatus(Integer numero, StatusMesa status) {
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mesa não encontrada com o número: " + numero));

        boolean possuiPedidoAtivo = possuiPedidoAtivoConsistente(mesa);
        if (possuiPedidoAtivo && status != StatusMesa.OCUPADA) {
            throw new ConflitoException("Uma mesa com pedido ativo deve permanecer OCUPADA.");
        }
        mesa.setStatus(status);
        mesaRepository.save(mesa);
    }

    @Transactional
    public void alterarAtivacao(Integer numero, boolean ativo) {
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mesa não encontrada com o número: " + numero));

        boolean possuiPedidoAtivo = possuiPedidoAtivoConsistente(mesa);
        if (possuiPedidoAtivo && !ativo) {
            throw new ConflitoException("Não é possível desativar uma mesa com pedido ativo.");
        }
        mesa.setAtivo(ativo);
        mesaRepository.save(mesa);
    }

    @Transactional
    public void excluir(Integer numero) {
        Mesa mesa = mesaRepository.findByNumero(numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mesa não encontrada com o número: " + numero));

        try {
            var credencial = usuarioRepository.findByMesaId(mesa.getId());
            if (credencial.isPresent()) {
                Usuario usuario = credencial.get();
                if (usuario.getRole() != RoleName.MESA) {
                    throw new ConflitoException("A mesa possui um vínculo de usuário incompatível. Revise a associação antes de excluir.");
                }
                usuarioRepository.delete(usuario);
                // Remove a FK da credencial antes da mesa; uma falha posterior reverte ambos.
                usuarioRepository.flush();
            }

            mesaRepository.delete(mesa);

            // Força o Hibernate a executar o delete no banco AGORA para validar Constraints
            mesaRepository.flush();

        } catch (DataIntegrityViolationException e) {
            throw new DataIntegrityViolationException("Não é possível excluir a Mesa " + numero + " pois existem históricos de pedidos vinculados a ela.");
        }
    }

    private boolean possuiPedidoAtivoConsistente(Mesa mesa) {
        var ativos = pedidoRepository.findAtivosByMesaNumero(mesa.getNumero());
        if (ativos.size() > 1 || (!ativos.isEmpty()
                && (!mesa.isAtivo() || mesa.getStatus() != StatusMesa.OCUPADA))) {
            throw new ConflitoException("A mesa e seus pedidos ativos estão inconsistentes. Revise os registros antes de continuar.");
        }
        return !ativos.isEmpty();
    }
}

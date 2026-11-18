package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.exception.ConflitoException;
import inf.anibele.pizzariamauabackend.mapper.MesaMapper;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.MesaRepository;
import inf.anibele.pizzariamauabackend.repository.UsuarioRepository;
import inf.anibele.pizzariamauabackend.security.AutorizacaoMesa;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProtecaoVinculoMesaTest {

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(value = RoleName.class, names = {"GERENTE", "COZINHA", "CAIXA"})
    void vinculoLegadoIncompativelNaoPermiteExcluirUsuarioAdministrativo(RoleName perfil) {
        MesaRepository mesas = mock(MesaRepository.class);
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        Mesa mesa = new Mesa();
        mesa.setId(1L);
        Usuario usuario = new Usuario();
        usuario.setRole(perfil);
        usuario.setMesa(mesa);
        when(mesas.findByNumero(1)).thenReturn(Optional.of(mesa));
        when(usuarios.findByMesaId(1L)).thenReturn(Optional.of(usuario));
        MesaService service = new MesaService(mesas, usuarios, mock(PasswordEncoder.class),
                new MesaMapper(), new AutorizacaoMesa(), mock(inf.anibele.pizzariamauabackend.repository.PedidoRepository.class));

        inf.anibele.pizzariamauabackend.LocksTeste.configurar(service, mesas);
        assertThrows(ConflitoException.class, () -> service.excluir(1));

        verify(usuarios, never()).delete(any());
        verify(usuarios, never()).flush();
        verify(mesas, never()).delete(any());
        verify(mesas, never()).flush();
        verify(usuarios, never()).findByUsername(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SEM_ID", "SEM_NUMERO"})
    void credencialComVinculoInvalidoFalhaFechada(String situacao) {
        Mesa mesa = new Mesa();
        if (!situacao.equals("SEM_ID")) mesa.setId(1L);
        if (!situacao.equals("SEM_NUMERO")) mesa.setNumero(1);
        Usuario usuario = new Usuario();
        usuario.setRole(RoleName.MESA);
        usuario.setMesa(mesa);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));

        assertThrows(AccessDeniedException.class, () -> new AutorizacaoMesa().verificarMesa(1));
    }
}

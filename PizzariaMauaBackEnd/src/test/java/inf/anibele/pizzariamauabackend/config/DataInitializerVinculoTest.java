package inf.anibele.pizzariamauabackend.config;

import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.MesaRepository;
import inf.anibele.pizzariamauabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataInitializerVinculoTest {

    private UsuarioRepository usuarios;
    private MesaRepository mesas;
    private PasswordEncoder encoder;
    private DataInitializer initializer;
    private Mesa mesa1;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        mesas = mock(MesaRepository.class);
        encoder = mock(PasswordEncoder.class);
        initializer = new DataInitializer();
        mesa1 = mesa(1);
        when(mesas.findByNumero(anyInt())).thenAnswer(invocation -> Optional.of(mesa(invocation.getArgument(0))));
        when(encoder.encode(anyString())).thenAnswer(invocation -> "hash:" + invocation.getArgument(0));
    }

    @Test
    void novasContasDemonstrativasRecebemVinculosESenhasOriginais() throws Exception {
        initializer.carregarUsuarios(usuarios, encoder, mesas).run();
        for (int i = 1; i <= 10; i++) {
            String nome = String.format("mesa%02d", i);
            int numero = i;
            verify(usuarios).save(argThat(usuario -> nome.equals(usuario.getUsername())
                    && usuario.getRole() == RoleName.MESA && usuario.getMesa().getNumero() == numero
                    && usuario.getSenha().equals("hash:" + nome)));
        }
        verify(usuarios, times(13)).save(any());
    }

    @Test
    void reinicioPreservaUsuarioValidoSemRecodificarSenha() throws Exception {
        Usuario existente = usuario(mesa1);
        when(usuarios.findByUsername("mesa01")).thenReturn(Optional.of(existente));
        initializer.carregarUsuarios(usuarios, encoder, mesas).run();
        verify(usuarios, never()).save(existente);
        verify(encoder, never()).encode("mesa01");
        assertSame(mesa1, existente.getMesa());
        assertEquals("hash-original", existente.getSenha());
    }

    @Test
    void legadoSemVinculoPermaneceIntactoParaMigracaoExplicita() throws Exception {
        Usuario existente = usuario(null);
        when(usuarios.findByUsername("mesa01")).thenReturn(Optional.of(existente));
        initializer.carregarUsuarios(usuarios, encoder, mesas).run();
        verify(usuarios, never()).save(existente);
        assertNull(existente.getMesa());
        assertEquals("hash-original", existente.getSenha());
    }

    @Test
    void vinculoConflitanteNaoESobrescrito() throws Exception {
        Mesa outra = mesa(2);
        Usuario existente = usuario(outra);
        when(usuarios.findByUsername("mesa01")).thenReturn(Optional.of(existente));
        initializer.carregarUsuarios(usuarios, encoder, mesas).run();
        verify(usuarios, never()).save(existente);
        assertSame(outra, existente.getMesa());
    }

    @Test
    void credencialPersonalizadaExistenteImpedeOutraContaParaMesmaMesa() throws Exception {
        when(usuarios.findByMesaId(1L)).thenReturn(Optional.of(usuario(mesa1)));
        initializer.carregarUsuarios(usuarios, encoder, mesas).run();
        verify(usuarios, never()).save(argThat(usuario -> "mesa01".equals(usuario.getUsername())));
    }

    @Test
    void mesasSaoOrdenadasAntesDasCredenciais() throws Exception {
        int ordemMesas = DataInitializer.class.getMethod("carregarMesas", MesaRepository.class).getAnnotation(Order.class).value();
        int ordemUsuarios = DataInitializer.class.getMethod("carregarUsuarios", UsuarioRepository.class,
                PasswordEncoder.class, MesaRepository.class).getAnnotation(Order.class).value();
        assertTrue(ordemMesas < ordemUsuarios);
    }

    private Mesa mesa(int numero) {
        Mesa mesa = new Mesa();
        mesa.setId((long) numero);
        mesa.setNumero(numero);
        return mesa;
    }

    private Usuario usuario(Mesa mesa) {
        Usuario usuario = new Usuario();
        usuario.setUsername("mesa01");
        usuario.setSenha("hash-original");
        usuario.setRole(RoleName.MESA);
        usuario.setMesa(mesa);
        return usuario;
    }
}

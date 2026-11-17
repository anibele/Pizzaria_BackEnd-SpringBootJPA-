package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.MesaRequestDTO;
import inf.anibele.pizzariamauabackend.exception.ConflitoException;
import inf.anibele.pizzariamauabackend.mapper.MesaMapper;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.*;
import inf.anibele.pizzariamauabackend.security.AutorizacaoMesa;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Contexto JPA explícito: nenhum @SpringBootTest, auto-configuração ou DataInitializer.
@SpringJUnitConfig(VinculoMesaJpaTest.ConfiguracaoTeste.class)
class VinculoMesaJpaTest {

    @Autowired private MesaService service;
    @Autowired private MesaRepository mesas;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder encoder;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManagerFactory emf;

    @AfterEach
    void limparSomenteH2() {
        reset(encoder);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(emf);
            em.createQuery("delete from Pedido").executeUpdate();
            em.createQuery("delete from Usuario").executeUpdate();
            em.createQuery("delete from Mesa").executeUpdate();
        });
    }

    @Test
    void cadastroComUsernamePersonalizadoPersisteVinculoEBCrypt() {
        service.cadastrar(new MesaRequestDTO(12, true, "tablet-sala", "senha-teste"));
        Usuario usuario = usuarios.findByUsername("tablet-sala").orElseThrow();
        assertEquals(12, usuario.getMesa().getNumero());
        assertEquals(mesas.findByNumero(12).orElseThrow().getId(), usuario.getMesa().getId());
        assertTrue(encoder.matches("senha-teste", usuario.getSenha()));
    }

    @Test
    void mesaSemCredencialContinuaPermitida() {
        service.cadastrar(new MesaRequestDTO(12, false, null, null));
        assertEquals(1, mesas.count());
        assertEquals(0, usuarios.count());
    }

    @Test
    void bancoImpedeDuasCredenciaisNaMesmaMesa() {
        Mesa mesa = mesas.saveAndFlush(mesa(12));
        usuarios.saveAndFlush(usuario("primeira", RoleName.MESA, mesa));
        assertThrows(DataIntegrityViolationException.class,
                () -> usuarios.saveAndFlush(usuario("segunda", RoleName.MESA, mesa)));
        assertEquals(1, usuarios.count());
    }

    @Test
    void persistenciaRejeitaMesaSemVinculoEPerfilAdministrativoVinculado() {
        assertThrows(jakarta.validation.ConstraintViolationException.class,
                () -> usuarios.saveAndFlush(usuario("sem-vinculo", RoleName.MESA, null)));
        Mesa mesa = mesas.saveAndFlush(mesa(12));
        assertThrows(jakarta.validation.ConstraintViolationException.class,
                () -> usuarios.saveAndFlush(usuario("gerente-vinculado", RoleName.GERENTE, mesa)));
        assertEquals(0, usuarios.count());
    }

    @Test
    void falhaNaCodificacaoReverteCadastroDaMesa() {
        doThrow(new IllegalArgumentException("falha simulada")).when(encoder).encode("falhar");
        assertThrows(IllegalArgumentException.class,
                () -> service.cadastrar(new MesaRequestDTO(12, true, "tablet", "falhar")));
        assertEquals(0, mesas.count());
        assertEquals(0, usuarios.count());
    }

    @Test
    void exclusaoRemoveSomenteCredencialAssociadaPreservandoHomonomoEOutraMesa() {
        service.cadastrar(new MesaRequestDTO(12, true, "personalizado", "senha"));
        service.cadastrar(new MesaRequestDTO(13, true, "outra-mesa", "senha"));
        usuarios.saveAndFlush(usuario("mesa12", RoleName.GERENTE, null));

        service.excluir(12);

        assertTrue(mesas.findByNumero(12).isEmpty());
        assertTrue(usuarios.findByUsername("personalizado").isEmpty());
        assertEquals(RoleName.GERENTE, usuarios.findByUsername("mesa12").orElseThrow().getRole());
        assertEquals(13, usuarios.findByUsername("outra-mesa").orElseThrow().getMesa().getNumero());
    }

    @Test
    void exclusaoSemCredencialNaoApagaUsuarioPorNome() {
        service.cadastrar(new MesaRequestDTO(12, false, null, null));
        usuarios.saveAndFlush(usuario("mesa12", RoleName.CAIXA, null));
        service.excluir(12);
        assertTrue(usuarios.findByUsername("mesa12").isPresent());
    }

    @Test
    void falhaPorHistoricoReverteExclusaoDaCredencial() {
        service.cadastrar(new MesaRequestDTO(12, true, "tablet-historico", "senha"));
        Usuario original = usuarios.findByUsername("tablet-historico").orElseThrow();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            EntityManager em = SharedEntityManagerCreator.createSharedEntityManager(emf);
            Pedido pedido = new Pedido();
            pedido.setMesa(em.getReference(Mesa.class, original.getMesa().getId()));
            pedido.setStatus(StatusPedido.FINALIZADO);
            pedido.setFaturamento(BigDecimal.TEN);
            pedido.setItens(new ArrayList<>());
            em.persist(pedido);
            em.flush();
        });

        assertThrows(DataIntegrityViolationException.class, () -> service.excluir(12));

        Usuario preservado = usuarios.findByUsername("tablet-historico").orElseThrow();
        assertEquals(original.getId(), preservado.getId());
        assertEquals(original.getSenha(), preservado.getSenha());
        assertEquals(original.getMesa().getId(), preservado.getMesa().getId());
        assertTrue(mesas.findByNumero(12).isPresent());
    }

    @Test
    void duplicidadeDeUsernameNaoCriaMesa() {
        usuarios.saveAndFlush(usuario("existente", RoleName.GERENTE, null));
        assertThrows(ConflitoException.class,
                () -> service.cadastrar(new MesaRequestDTO(12, true, "existente", "senha")));
        assertEquals(0, mesas.count());
        assertEquals(1, usuarios.count());
    }

    @Test
    void falhaNaPersistenciaDaCredencialReverteMesaJaInserida() {
        // Excede o VARCHAR(255) existente, forçando falha no INSERT do usuário após salvar a mesa.
        assertThrows(DataIntegrityViolationException.class,
                () -> service.cadastrar(new MesaRequestDTO(12, true, "u".repeat(300), "senha")));
        assertEquals(0, mesas.count());
        assertEquals(0, usuarios.count());
    }

    private Mesa mesa(int numero) {
        Mesa mesa = new Mesa();
        mesa.setNumero(numero);
        mesa.setStatus(StatusMesa.LIVRE);
        return mesa;
    }

    private Usuario usuario(String nome, RoleName role, Mesa mesa) {
        Usuario usuario = new Usuario();
        usuario.setUsername(nome);
        usuario.setSenha("hash-de-teste");
        usuario.setRole(role);
        usuario.setMesa(mesa);
        return usuario;
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UsuarioRepository.class,
            excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = {ProdutoRepository.class, PedidoRepository.class, ItemPedidoRepository.class}))
    static class ConfiguracaoTeste {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:fase3a-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("inf.anibele.pizzariamauabackend.model");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }
        @Bean PasswordEncoder passwordEncoder() { return spy(new BCryptPasswordEncoder()); }
        @Bean MesaService mesaService(MesaRepository mesas, UsuarioRepository usuarios, PasswordEncoder encoder) {
            return new MesaService(mesas, usuarios, encoder, new MesaMapper(), new AutorizacaoMesa());
        }
    }
}

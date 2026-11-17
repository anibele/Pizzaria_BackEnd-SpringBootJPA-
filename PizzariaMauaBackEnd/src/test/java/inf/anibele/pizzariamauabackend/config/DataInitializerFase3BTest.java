package inf.anibele.pizzariamauabackend.config;

import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.*;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(DataInitializerFase3BTest.Config.class)
@ActiveProfiles("demo")
class DataInitializerFase3BTest {
    @Autowired UsuarioRepository usuarios;
    @Autowired MesaRepository mesas;
    @Autowired ProdutoRepository produtos;
    @Autowired PedidoRepository pedidos;
    @Autowired ItemPedidoRepository itens;
    @Autowired CommandLineRunner carregarDadosDemonstrativos;
    @Autowired PlatformTransactionManager tx;
    @Autowired EntityManagerFactory emf;
    @Autowired PasswordEncoder encoder;

    @BeforeEach void limparSomenteH2() {
        reset(encoder);
        when(encoder.encode(anyString())).thenAnswer(i->new BCryptPasswordEncoder(4).encode(i.getArgument(0)));
        new TransactionTemplate(tx).executeWithoutResult(status->{
            var em=SharedEntityManagerCreator.createSharedEntityManager(emf);
            for (String entity:List.of("ItemPedido","Pedido","Usuario","Produto","Mesa")) em.createQuery("delete from "+entity).executeUpdate();
        });
    }
    @Test void cargaCompletaRespeitaOrdemVinculosECredenciais() throws Exception {
        carregarDadosDemonstrativos.run();
        assertEquals(10,mesas.count());assertEquals(13,usuarios.count());assertEquals(34,produtos.count());
        for (var u:usuarios.findAll()) {
            assertTrue(new BCryptPasswordEncoder().matches(u.getUsername(),u.getSenha()));
            if (u.getRole()==RoleName.MESA) {
                assertNotNull(u.getMesa());assertNotNull(u.getMesa().getId());assertTrue(u.isVinculoMesaValido());
                assertEquals(mesas.findByNumero(u.getMesa().getNumero()).orElseThrow().getId(),u.getMesa().getId());
            } else assertNull(u.getMesa());
        }
        assertEquals(10,usuarios.findAll().stream().filter(u->u.getRole()==RoleName.MESA).map(u->u.getMesa().getId()).distinct().count());
    }
    @Test void reinicioNaoAtualizaUsuariosMesasOuProdutos() throws Exception {
        carregarDadosDemonstrativos.run();
        var antes=usuarios.findAll();var mesasAntes=mesas.findAll();
        clearInvocations(encoder);carregarDadosDemonstrativos.run();
        assertEquals(antes,usuarios.findAll());assertEquals(mesasAntes,mesas.findAll());assertEquals(34,produtos.count());verifyNoInteractions(encoder);
    }
    @ParameterizedTest @ValueSource(strings={"produto","mesa","usuario"})
    void exclusaoNaoERecriada(String recurso) throws Exception {
        carregarDadosDemonstrativos.run();
        if (recurso.equals("produto")) produtos.deleteById(produtos.findAll().get(0).getId());
        if (recurso.equals("usuario")) usuarios.deleteById(usuarios.findByUsername("gerente1").orElseThrow().getId());
        if (recurso.equals("mesa")) {
            usuarios.deleteById(usuarios.findByUsername("mesa01").orElseThrow().getId());
            mesas.deleteById(mesas.findByNumero(1).orElseThrow().getId());
        }
        long u=usuarios.count(),m=mesas.count(),p=produtos.count();
        carregarDadosDemonstrativos.run();assertEquals(u,usuarios.count());assertEquals(m,mesas.count());assertEquals(p,produtos.count());
    }
    @ParameterizedTest @ValueSource(strings={"usuario","mesa","produto"})
    void baseParcialNaoECompletada(String recurso) throws Exception {
        if (recurso.equals("mesa")) {var m=new Mesa();m.setNumero(99);m.setAtivo(true);m.setStatus(StatusMesa.LIVRE);mesas.save(m);}
        if (recurso.equals("usuario")) {var u=new Usuario();u.setUsername("personalizado");u.setSenha("hash-preservado");u.setRole(RoleName.GERENTE);usuarios.save(u);}
        if (recurso.equals("produto")) {var p=new Produto();p.setDescricao(new DetalhesDescricao());p.setNome("Duplicado");produtos.save(p);var outro=new Produto();outro.setNome("Duplicado");produtos.save(outro);}
        long u=usuarios.count(),m=mesas.count(),p=produtos.count();
        carregarDadosDemonstrativos.run();assertEquals(u,usuarios.count());assertEquals(m,mesas.count());assertEquals(p,produtos.count());verifyNoInteractions(encoder);
    }
    @Test void historicoPermaneceInalterado() throws Exception {
        var m=new Mesa();m.setNumero(99);m.setAtivo(false);m.setStatus(StatusMesa.LIVRE);mesas.save(m);
        var p=new Produto();p.setDescricao(new DetalhesDescricao());p.setNome("Produto historico");produtos.save(p);
        var pedido=new Pedido();pedido.setMesa(m);pedido.setStatus(StatusPedido.FINALIZADO);
        pedido.setFaturamento(new java.math.BigDecimal("42.50"));
        pedido.setDataHora(java.time.LocalDateTime.of(2026,1,1,12,0));pedidos.save(pedido);
        var item=new ItemPedido();item.setPedido(pedido);item.setProduto(p);item.setQuantidade(2);
        item.setPrecoUnitario(new java.math.BigDecimal("21.25"));itens.save(item);
        carregarDadosDemonstrativos.run();
        var depois=pedidos.findById(pedido.getId()).orElseThrow();
        assertEquals(pedido.getFaturamento(),depois.getFaturamento());assertEquals(pedido.getDataHora(),depois.getDataHora());
        assertEquals(StatusPedido.FINALIZADO,depois.getStatus());assertEquals(m.getId(),depois.getMesa().getId());
        assertEquals(1,pedidos.count());assertEquals(1,itens.count());assertEquals(0,usuarios.count());
        assertEquals(item.getPrecoUnitario(),itens.findById(item.getId()).orElseThrow().getPrecoUnitario());
        assertFalse(mesas.findById(m.getId()).orElseThrow().isAtivo());
    }
    @Test void falhaNaCargaReverteMesasEUsuarios() {
        when(encoder.encode("cozinha1")).thenThrow(new IllegalStateException("falha simulada"));
        assertThrows(IllegalStateException.class,()->carregarDadosDemonstrativos.run());
        assertEquals(0,usuarios.count());assertEquals(0,mesas.count());assertEquals(0,produtos.count());
    }
    @ParameterizedTest @ValueSource(strings={"default","prod","demo,prod"})
    void perfisNaoDemonstrativosNaoRegistramSeed(String perfil) {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(perfil.split(","));context.register(DataInitializer.class);context.refresh();
            assertTrue(context.getBeansOfType(CommandLineRunner.class).isEmpty());
        }
    }
    @Configuration @EnableJpaRepositories(basePackageClasses=UsuarioRepository.class) @Import(DataInitializer.class)
    static class Config {
        @Bean DataSource dataSource() {return new DriverManagerDataSource("jdbc:h2:mem:seedsfase3b;DB_CLOSE_DELAY=-1","sa","");}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("inf.anibele.pizzariamauabackend.model");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) {return new JpaTransactionManager(emf);}
        @Bean PasswordEncoder encoder() {return mock(PasswordEncoder.class);}
    }
}

package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.*;
import inf.anibele.pizzariamauabackend.exception.ConflitoException;
import inf.anibele.pizzariamauabackend.exception.RecursoNaoEncontradoException;
import inf.anibele.pizzariamauabackend.mapper.*;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.*;
import inf.anibele.pizzariamauabackend.security.AutorizacaoMesa;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

// Apenas contexto JPA explicito e H2: sem Spring Boot, JWT, seeds ou MySQL.
@SpringJUnitConfig(EstadosPedidosJpaTest.Config.class)
class EstadosPedidosJpaTest {
    @Autowired PedidoService pedidosService;
    @Autowired MesaService mesasService;
    @Autowired PedidoRepository pedidos;
    @Autowired MesaRepository mesas;
    @Autowired ProdutoRepository produtos;
    @Autowired PlatformTransactionManager tx;
    @Autowired DataSource dataSource;
    private JdbcTemplate jdbc;
    private Mesa mesa;
    private Produto produto;

    @BeforeEach void preparar() {
        assertTrue(((DriverManagerDataSource)dataSource).getUrl().startsWith("jdbc:h2:mem:"));
        jdbc=new JdbcTemplate(dataSource);
        for (String tabela:List.of("item_pedido","pedido","usuario","produto_ingredientes","produto","mesa")) jdbc.update("DELETE FROM "+tabela);
        mesa=new Mesa();mesa.setNumero(1);mesa.setStatus(StatusMesa.LIVRE);mesas.save(mesa);
        produto=new Produto();produto.setNome("Pizza teste");produto.setAtivo(true);produto.setQtdEstoque(10);
        produto.setPrecoBase(new BigDecimal("15.00"));produto.setDescricao(new DetalhesDescricao());produtos.save(produto);
    }
    static Stream<Arguments> abertura() {
        List<StatusPedido> estados=new ArrayList<>();estados.add(null);estados.addAll(List.of(StatusPedido.values()));
        return Stream.of(true,false).flatMap(ativo->Stream.of(StatusMesa.values()).flatMap(status->estados.stream()
                .map(pedido->Arguments.of(ativo,status,pedido))));
    }
    @ParameterizedTest(name="abertura ativo={0} mesa={1} pedido anterior={2}") @MethodSource("abertura")
    void aberturaRespeitaDisponibilidadeEHistorico(boolean ativo, StatusMesa estado, StatusPedido anterior) {
        mesa.setAtivo(ativo);mesa.setStatus(estado);mesas.save(mesa);
        if (anterior!=null) conta(anterior);
        var antes=snapshot();
        boolean permitido=ativo && estado==StatusMesa.LIVRE && (anterior==null || anterior==StatusPedido.FINALIZADO || anterior==StatusPedido.CANCELADO);
        if (!permitido) {
            assertThrows(ConflitoException.class,()->pedidosService.abrirPedido(requisicao(2)));
            assertEquals(antes,snapshot());
        } else {
            var resposta=pedidosService.abrirPedido(requisicao(2));
            assertEquals(StatusPedido.ABERTO,resposta.status());assertEquals(1,resposta.numeroMesa());
            assertEquals(new BigDecimal("30.00"),resposta.faturamento());assertEquals(2,resposta.itens().get(0).quantidade());
            assertEquals(8,produtos.findById(produto.getId()).orElseThrow().getQtdEstoque());
            assertEquals(StatusMesa.OCUPADA,mesas.findById(mesa.getId()).orElseThrow().getStatus());
            assertEquals(1,pedidos.findAtivosByMesaNumero(1).size());
        }
    }
    @Test void cicloCompletoBloqueiaNovaContaAteConfirmarPagamento() {
        var aberto=pedidosService.abrirPedido(requisicao(1));
        var aguardando=pedidosService.finalizarPedido(aberto.id(),"PIX");
        assertEquals(StatusPedido.AGUARDANDO_PAGAMENTO,aguardando.status());assertEquals("PIX",aguardando.formaPagamento());
        assertEquals(StatusMesa.OCUPADA,mesas.findById(mesa.getId()).orElseThrow().getStatus());
        var antes=snapshot();
        assertThrows(ConflitoException.class,()->pedidosService.abrirPedido(requisicao(1)));assertEquals(antes,snapshot());
        assertThrows(ConflitoException.class,()->pedidosService.finalizarPedido(aberto.id(),"DINHEIRO"));assertEquals(antes,snapshot());
        var pago=pedidosService.confirmarPagamentoEFecharMesa(aberto.id());
        assertEquals(StatusPedido.FINALIZADO,pago.status());assertEquals(aberto.faturamento(),pago.faturamento());
        assertEquals(StatusMesa.LIVRE,mesas.findById(mesa.getId()).orElseThrow().getStatus());
        var depois=snapshot();assertThrows(ConflitoException.class,()->pedidosService.confirmarPagamentoEFecharMesa(aberto.id()));assertEquals(depois,snapshot());
        assertNotEquals(aberto.id(),pedidosService.abrirPedido(requisicao(1)).id());
    }
    @ParameterizedTest @EnumSource(value=StatusPedido.class,names={"ABERTO","AGUARDANDO_PAGAMENTO"})
    void consultaReconheceAmbosOsEstadosAtivos(StatusPedido status) {
        mesa.setStatus(StatusMesa.OCUPADA);mesas.save(mesa);var conta=conta(status);
        var resposta=new TransactionTemplate(tx).execute(s->pedidosService.buscarPedidoAbertoDaMesa(1));
        assertEquals(conta.getId(),resposta.id());assertEquals(status,resposta.status());
    }
    @ParameterizedTest @EnumSource(value=StatusPedido.class,names={"FINALIZADO","CANCELADO"})
    void contasEncerradasNaoSaoAtivas(StatusPedido status) {
        conta(status);assertTrue(pedidos.findAtivosByMesaNumero(1).isEmpty());
        assertThrows(RecursoNaoEncontradoException.class,()->pedidosService.buscarPedidoAbertoDaMesa(1));
    }
    @ParameterizedTest @EnumSource(value=StatusPedido.class,names={"ABERTO","AGUARDANDO_PAGAMENTO"})
    void multiplasContasBloqueiamConsultaFechamentoConfirmacaoEAbertura(StatusPedido outroStatus) {
        mesa.setStatus(StatusMesa.OCUPADA);mesas.save(mesa);
        var primeiro=conta(StatusPedido.AGUARDANDO_PAGAMENTO);var outro=conta(outroStatus);var antes=snapshot();
        assertThrows(ConflitoException.class,()->pedidosService.buscarPedidoAbertoDaMesa(1));
        assertThrows(ConflitoException.class,()->pedidosService.confirmarPagamentoEFecharMesa(primeiro.getId()));
        if(outroStatus==StatusPedido.ABERTO) assertThrows(ConflitoException.class,()->pedidosService.finalizarPedido(outro.getId(),"PIX"));
        assertThrows(ConflitoException.class,()->pedidosService.abrirPedido(requisicao(1)));
        assertThrows(ConflitoException.class,()->mesasService.atualizarStatus(1,StatusMesa.LIVRE));
        assertThrows(ConflitoException.class,()->mesasService.alterarAtivacao(1,false));
        assertEquals(antes,snapshot());
    }
    @ParameterizedTest @EnumSource(value=StatusPedido.class,names={"ABERTO","FINALIZADO","CANCELADO"})
    void confirmacaoExigeAguardarPagamento(StatusPedido status) {
        var pedido=conta(status);var antes=snapshot();
        assertThrows(ConflitoException.class,()->pedidosService.confirmarPagamentoEFecharMesa(pedido.getId()));assertEquals(antes,snapshot());
    }
    @ParameterizedTest @EnumSource(value=StatusPedido.class,names={"AGUARDANDO_PAGAMENTO","FINALIZADO","CANCELADO"})
    void fechamentoRejeitaEstadosDiferentesDeAberto(StatusPedido status) {
        mesa.setStatus(StatusMesa.OCUPADA);mesas.save(mesa);var pedido=conta(status);var antes=snapshot();
        assertThrows(ConflitoException.class,()->pedidosService.finalizarPedido(pedido.getId(),"PIX"));assertEquals(antes,snapshot());
    }
    @Test void mesaInativaComContaAtivaNaoEReparadaPorTransicoes() {
        mesa.setAtivo(false);mesa.setStatus(StatusMesa.OCUPADA);mesas.save(mesa);var pedido=conta(StatusPedido.ABERTO);
        var antes=snapshot();assertThrows(ConflitoException.class,()->pedidosService.finalizarPedido(pedido.getId(),"PIX"));assertEquals(antes,snapshot());
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);pedidos.save(pedido);antes=snapshot();
        assertThrows(ConflitoException.class,()->pedidosService.confirmarPagamentoEFecharMesa(pedido.getId()));
        assertThrows(ConflitoException.class,()->mesasService.alterarAtivacao(1,true));assertEquals(antes,snapshot());
    }
    @ParameterizedTest @EnumSource(value=StatusMesa.class,names={"LIVRE","RESERVADA","MANUTENCAO"})
    void mesaInconsistenteImpedeTransicoesSemCorrecaoAutomatica(StatusMesa status) {
        mesa.setStatus(status);mesas.save(mesa);var pedido=conta(StatusPedido.ABERTO);var antes=snapshot();
        assertThrows(ConflitoException.class,()->pedidosService.finalizarPedido(pedido.getId(),"PIX"));assertEquals(antes,snapshot());
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);pedidos.save(pedido);antes=snapshot();
        assertThrows(ConflitoException.class,()->pedidosService.confirmarPagamentoEFecharMesa(pedido.getId()));assertEquals(antes,snapshot());
        assertThrows(ConflitoException.class,()->mesasService.atualizarStatus(1,StatusMesa.OCUPADA));assertEquals(antes,snapshot());
    }
    @ParameterizedTest @EnumSource(value=StatusPedido.class,names={"ABERTO","AGUARDANDO_PAGAMENTO"})
    void contaAtivaImpedeAdministracaoIncompativel(StatusPedido estado) {
        mesa.setStatus(StatusMesa.OCUPADA);mesas.save(mesa);conta(estado);var antes=snapshot();
        for (var status:List.of(StatusMesa.LIVRE,StatusMesa.RESERVADA,StatusMesa.MANUTENCAO)) {
            assertThrows(ConflitoException.class,()->mesasService.atualizarStatus(1,status));assertEquals(antes,snapshot());
        }
        assertThrows(ConflitoException.class,()->mesasService.alterarAtivacao(1,false));assertEquals(antes,snapshot());
        mesasService.atualizarStatus(1,StatusMesa.OCUPADA);mesasService.alterarAtivacao(1,true);assertEquals(antes,snapshot());
    }
    @ParameterizedTest @EnumSource(StatusMesa.class)
    void administracaoSemPedidoAtivoPermaneceDisponivel(StatusMesa status) {
        mesasService.atualizarStatus(1,status);assertEquals(status,mesas.findById(mesa.getId()).orElseThrow().getStatus());
        mesasService.alterarAtivacao(1,false);assertFalse(mesas.findById(mesa.getId()).orElseThrow().isAtivo());
        mesasService.alterarAtivacao(1,true);assertTrue(mesas.findById(mesa.getId()).orElseThrow().isAtivo());
    }
    @Test void rollbackReverteEstoqueSeItemPosteriorFalhar() {
        var semEstoque=new Produto();semEstoque.setNome("Sem estoque");semEstoque.setAtivo(true);semEstoque.setQtdEstoque(0);
        semEstoque.setPrecoBase(BigDecimal.TEN);semEstoque.setDescricao(new DetalhesDescricao());produtos.save(semEstoque);
        var antes=snapshot();
        var dto=new PedidoRequestDTO(1,List.of(new ItemPedidoRequestDTO(produto.getId(),1),new ItemPedidoRequestDTO(semEstoque.getId(),1)),null);
        assertThrows(ConflitoException.class,()->pedidosService.abrirPedido(dto));assertEquals(antes,snapshot());
    }
    private Pedido conta(StatusPedido status) {
        var p=new Pedido();p.setMesa(mesa);p.setStatus(status);p.setDataHora(LocalDateTime.of(2026,1,1,12,0));
        p.setFaturamento(new BigDecimal("55.00"));p.setItens(new ArrayList<>());return pedidos.save(p);
    }
    private PedidoRequestDTO requisicao(int quantidade) {return new PedidoRequestDTO(1,List.of(new ItemPedidoRequestDTO(produto.getId(),quantidade)),null);}
    private Map<String,List<Map<String,Object>>> snapshot() {
        var dados=new LinkedHashMap<String,List<Map<String,Object>>>();
        for (String tabela:List.of("usuario","mesa","pedido","item_pedido","produto","produto_ingredientes")) {
            dados.put(tabela,jdbc.queryForList("SELECT * FROM "+tabela+" ORDER BY "+(tabela.equals("produto_ingredientes")?"produto_id,ingrediente":"id")));
        }
        return dados;
    }
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=PedidoRepository.class)
    static class Config {
        @Bean DataSource dataSource() {return new DriverManagerDataSource("jdbc:h2:mem:estados4a-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("inf.anibele.pizzariamauabackend.model");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) {return new JpaTransactionManager(emf);}
        @Bean PedidoService pedidoService(PedidoRepository p,MesaRepository m,ProdutoRepository pr,ItemPedidoRepository i) {
            return new PedidoService(p,m,pr,i,new PedidoMapper(new ItemPedidoMapper()),mock(AutorizacaoMesa.class));
        }
        @Bean MesaService mesaService(MesaRepository m,UsuarioRepository u,PedidoRepository p) {
            return new MesaService(m,u,mock(PasswordEncoder.class),new MesaMapper(),mock(AutorizacaoMesa.class),p);
        }
    }
}

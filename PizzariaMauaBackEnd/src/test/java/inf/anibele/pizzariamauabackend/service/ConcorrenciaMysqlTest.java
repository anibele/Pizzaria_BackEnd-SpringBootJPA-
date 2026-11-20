package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.*;
import inf.anibele.pizzariamauabackend.exception.ConflitoException;
import inf.anibele.pizzariamauabackend.mapper.*;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.*;
import inf.anibele.pizzariamauabackend.security.AutorizacaoMesa;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import java.time.LocalDateTime;
import inf.anibele.pizzariamauabackend.controller.*;
import inf.anibele.pizzariamauabackend.exception.ApiExceptionHandler;
import inf.anibele.pizzariamauabackend.exception.RecursoNaoEncontradoException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

// Opt-in: destino verificado ANTES de qualquer DDL. Nunca usa application.properties.
@EnabledIfEnvironmentVariable(named="TEST_MYSQL_URL",matches="jdbc:mysql://127[.]0[.]0[.]1:.*")
@SpringJUnitConfig(ConcorrenciaMysqlTest.Config.class)
class ConcorrenciaMysqlTest {
    @Autowired PedidoService service;
    @Autowired ProdutoService produtoService;
    @Autowired MesaService mesaService;
    @Autowired MesaRepository mesas;
    @Autowired ProdutoRepository produtos;
    @Autowired PedidoRepository pedidos;
    @Autowired UsuarioRepository usuarios;
    @Autowired DataSource ds;
    @Autowired PlatformTransactionManager tm;
    @Autowired EntityManager em;
    private JdbcTemplate jdbc;
    private Produto a,b;
    static final Set<String> LOCK_SQL=ConcurrentHashMap.newKeySet();
    @AfterAll static void sqlDosLocks(){LOCK_SQL.stream().sorted().forEach(s->System.out.println("MYSQL_LOCK_SQL "+s));}
    static final Queue<String> SQL=new ConcurrentLinkedQueue<>();

    @BeforeEach void preparar() {
        jdbc=new JdbcTemplate(ds);
        for(String t:List.of("idempotencia_adicao","item_pedido","pedido","usuario","produto_ingredientes","produto","mesa")) jdbc.update("delete from "+t);
        a=produto("A",50,10);b=produto("B",30,10);
        for(int i=1;i<=8;i++){var m=new Mesa();m.setNumero(i);m.setStatus(StatusMesa.LIVRE);mesas.save(m);}
        SQL.clear();
    }
    @AfterEach void integridade() {
        assertEquals(0,jdbc.queryForObject("select count(*) from produto where qtd_estoque<0",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from (select mesa_id from pedido where status in ('ABERTO','AGUARDANDO_PAGAMENTO') group by mesa_id having count(*)>1) d",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from pedido p where p.faturamento <> (select coalesce(sum(i.quantidade*i.preco_unitario),0) from item_pedido i where i.pedido_id=p.id)",Integer.class));
        System.out.println("MYSQL_ESTADO produtos="+jdbc.queryForList("select nome,qtd_estoque from produto order by id")+" pedidos="+jdbc.queryForList("select status,faturamento from pedido order by id"));
    }
    @Test void A_ultimaUnidade() throws Exception {
        estoque(a,1);
        var resultados=concorrer("produto",a.getId(),List.of(()->abrir(1,a,1),()->abrir(2,a,1)));
        assertEquals(1,sucessos(resultados));assertEquals(0,saldo(a));assertEquals(1,quantidade(a));
    }
    @Test void B_variasUnidades() throws Exception {
        List<Callable<Object>> tarefas=new ArrayList<>();for(int i=1;i<=6;i++){int n=i;tarefas.add(()->abrir(n,a,2));}
        var r=concorrer("produto",a.getId(),tarefas);assertEquals(5,sucessos(r));assertEquals(0,saldo(a));assertEquals(10,quantidade(a));
    }
    @Test void C_duplaAbertura() throws Exception {
        var r=concorrer("mesa",idMesa(1),List.of(()->abrir(1,a,1),()->abrir(1,a,1)));
        assertEquals(1,sucessos(r));assertEquals(1,pedidos.findAtivosByMesaNumero(1).size());assertEquals(StatusMesa.OCUPADA,mesas.findByNumero(1).orElseThrow().getStatus());
    }
    @Test void D_adicoesSimultaneas() throws Exception {
        var p=abrir(1,a,2);
        var r=concorrer("mesa",idMesa(1),List.of(()->service.adicionarItens(p.id(),itens(a,1)),()->service.adicionarItens(p.id(),itens(b,1))));
        assertEquals(2,sucessos(r));assertEquals(new BigDecimal("180.00"),pedidos.findById(p.id()).orElseThrow().getFaturamento());
        assertEquals(7,saldo(a));assertEquals(9,saldo(b));assertEquals(3,jdbc.queryForObject("select count(*) from item_pedido",Integer.class));
    }
    @Test void E_adicaoVersusFechamento() throws Exception {
        var p=abrir(1,a,2);service.atualizarStatusItem(p.itens().get(0).id(),StatusItemPedido.PRONTO);
        var r=concorrer("mesa",idMesa(1),List.of(()->service.adicionarItens(p.id(),itens(b,1)),()->service.finalizarPedido(p.id(),"PIX")));
        assertEquals(1,sucessos(r));
        var atual=pedidos.findById(p.id()).orElseThrow();
        assertTrue(atual.getStatus()==StatusPedido.ABERTO || atual.getStatus()==StatusPedido.AGUARDANDO_PAGAMENTO);
        boolean adicionou=atual.getStatus()==StatusPedido.ABERTO;
        assertEquals(adicionou?new BigDecimal("130.00"):new BigDecimal("100.00"),atual.getFaturamento());
        assertEquals(adicionou?9:10,saldo(b));assertEquals(StatusMesa.OCUPADA,mesas.findByNumero(1).orElseThrow().getStatus());
    }
    @Test void F_pagamentoSimultaneo() throws Exception {
        var p=abrir(1,a,1);service.atualizarStatusItem(p.itens().get(0).id(),StatusItemPedido.PRONTO);service.finalizarPedido(p.id(),"PIX");
        var r=concorrer("mesa",idMesa(1),List.of(()->service.confirmarPagamentoEFecharMesa(p.id()),()->service.confirmarPagamentoEFecharMesa(p.id())));
        assertEquals(1,sucessos(r));assertEquals(StatusPedido.FINALIZADO,pedidos.findById(p.id()).orElseThrow().getStatus());
        assertEquals(StatusMesa.LIVRE,mesas.findByNumero(1).orElseThrow().getStatus());assertEquals(9,saldo(a));
    }
    @Test void G_edicaoEVenda() throws Exception {
        var dto=new ProdutoRequestDTO("Editado",new DetalhesDescricaoDTO(null,null,false,new ArrayList<>()),Categoria.PIZZAS,new BigDecimal("50.00"),null,true,20);
        var r=concorrer("produto",a.getId(),List.of(()->produtoService.editar(a.getId(),dto),()->abrir(1,a,1)));
        assertEquals(2,sucessos(r));assertTrue(saldo(a)==19 || saldo(a)==20);assertEquals("Editado",produtos.findById(a.getId()).orElseThrow().getNome());assertEquals(1,quantidade(a));
    }
    @Test void G2_statusAdministrativoNaoSobrescreveBaixa() throws Exception {
        var r=concorrer("produto",a.getId(),List.of(()->produtoService.alternarStatus(a.getId(),true),()->abrir(1,a,1)));
        assertEquals(2,sucessos(r));assertEquals(9,saldo(a));
    }
    @Test void G3_desativacaoEVenda() throws Exception {
        var r=concorrer("produto",a.getId(),List.of(()->produtoService.alternarStatus(a.getId(),false),()->abrir(1,a,1)));
        assertFalse(produtos.findById(a.getId()).orElseThrow().isAtivo());
        assertEquals(sucessos(r)==2?9:10,saldo(a));assertEquals(sucessos(r)==2?1:0,quantidade(a));
    }
    @Test void G4_exclusaoEVenda() throws Exception {
        var r=concorrer("produto",a.getId(),List.of(()->{produtoService.excluir(a.getId());return "excluido";},()->abrir(1,a,1)));
        assertEquals(1,sucessos(r));
        if(produtos.existsById(a.getId())){assertEquals(1,quantidade(a));assertEquals(9,saldo(a));}
        else assertEquals(0,quantidade(a));
    }
    @Test void H_ordensInversas() throws Exception {
        var r=concorrer("produto",a.getId(),List.of(
                ()->service.abrirPedido(new PedidoRequestDTO(1,List.of(new ItemPedidoRequestDTO(b.getId(),1),new ItemPedidoRequestDTO(a.getId(),1)),null)),
                ()->service.abrirPedido(new PedidoRequestDTO(2,List.of(new ItemPedidoRequestDTO(a.getId(),1),new ItemPedidoRequestDTO(b.getId(),1)),null))));
        assertEquals(2,sucessos(r));assertEquals(8,saldo(a));assertEquals(8,saldo(b));
    }
    @Test void I_rollbackIntegral() {
        var antes=snapshot();var tx=transacao();
        assertThrows(ConflitoException.class,()->tx.execute(s->{abrir(1,a,1);throw new ConflitoException("Falha controlada antes do commit");}));
        assertEquals(antes,snapshot());
    }
    @Test void J_regras4A() {
        for(var status:List.of(StatusMesa.RESERVADA,StatusMesa.MANUTENCAO,StatusMesa.OCUPADA)) {
            mesaService.atualizarStatus(1,status);var antes=snapshot();assertThrows(ConflitoException.class,()->abrir(1,a,1));assertEquals(antes,snapshot());
        }
        mesaService.atualizarStatus(1,StatusMesa.LIVRE);mesaService.alterarAtivacao(1,false);
        assertThrows(ConflitoException.class,()->abrir(1,a,1));mesaService.alterarAtivacao(1,true);
        var p=abrir(1,a,1);service.atualizarStatusItem(p.itens().get(0).id(),StatusItemPedido.PRONTO);service.finalizarPedido(p.id(),"PIX");var antes=snapshot();
        assertThrows(ConflitoException.class,()->abrir(1,a,1));assertEquals(antes,snapshot());
        assertThrows(ConflitoException.class,()->mesaService.atualizarStatus(1,StatusMesa.LIVRE));
        service.confirmarPagamentoEFecharMesa(p.id());assertEquals(StatusMesa.LIVRE,mesas.findByNumero(1).orElseThrow().getStatus());
    }
    @Test void J2_multiplasContasLegadasSaoRejeitadas() {
        var p=abrir(1,a,1);var antes=snapshot();
        transacao().executeWithoutResult(st->{
            var duplicado=new Pedido();duplicado.setMesa(mesas.findByNumero(1).orElseThrow());duplicado.setStatus(StatusPedido.ABERTO);
            duplicado.setFaturamento(BigDecimal.ZERO);duplicado.setItens(new ArrayList<>());pedidos.saveAndFlush(duplicado);
            assertThrows(ConflitoException.class,()->service.buscarPedidoAbertoDaMesa(1));
            assertThrows(ConflitoException.class,()->service.finalizarPedido(p.id(),"PIX"));
            assertThrows(ConflitoException.class,()->mesaService.atualizarStatus(1,StatusMesa.LIVRE));
            st.setRollbackOnly();
        });
        assertEquals(antes,snapshot());
    }
    @Test void K_itensRepetidosEOverflowNaoVendemAcimaDoEstoque() {
        var antes=snapshot();
        assertThrows(ConflitoException.class,()->service.abrirPedido(new PedidoRequestDTO(1,List.of(new ItemPedidoRequestDTO(a.getId(),Integer.MAX_VALUE),new ItemPedidoRequestDTO(a.getId(),Integer.MAX_VALUE)),null)));
        assertEquals(antes,snapshot());
        var p=service.abrirPedido(new PedidoRequestDTO(1,List.of(new ItemPedidoRequestDTO(a.getId(),3),new ItemPedidoRequestDTO(a.getId(),4)),null));
        assertEquals(new BigDecimal("350.00"),p.faturamento());assertEquals(3,saldo(a));assertEquals(7,quantidade(a));
    }
    @Test void L_timeoutReverteOperacao() throws Exception {
        var antes=snapshot();var pool=Executors.newSingleThreadExecutor();
        try(var holder=ds.getConnection()) {
            holder.setAutoCommit(false);holder.createStatement().executeQuery("select id from produto where id="+a.getId()+" for update").close();
            var future=pool.submit(()->abrir(1,a,1));
            var e=assertThrows(ExecutionException.class,()->future.get(12,TimeUnit.SECONDS));assertInstanceOf(PessimisticLockingFailureException.class,e.getCause());
            holder.rollback();assertEquals(antes,snapshot());
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    @Test void M_contextoAntigoNaoSobrescreveEdicaoConfirmada() throws Exception {
        // Simula entidade ja carregada pelo contexto antes de outra transacao confirmar.
        var ready=new CountDownLatch(1);var edited=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var f=pool.submit(()->transacao().execute(s->{em.find(Produto.class,a.getId());ready.countDown();aguardar(edited);return abrir(1,a,1);}));
            assertTrue(ready.await(8,TimeUnit.SECONDS));estoque(a,2);edited.countDown();f.get(15,TimeUnit.SECONDS);assertEquals(1,saldo(a));
        } finally {edited.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    @Test void N_leiturasNaoAdquiremLocksDeEscrita() {
        SQL.clear();produtoService.listarAtivos();mesaService.buscarPorNumero(1);
        assertTrue(SQL.stream().noneMatch(s->s.toLowerCase().contains("for update")));
    }
    @Test void O_deadlockInduzidoForaDaOrdemDosServices() throws Exception {
        // Somente para testar traducao/rollback: repositories chamados em ordem deliberadamente errada.
        var antes=snapshot();var ready=new CountDownLatch(2);var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Object>> futures=new ArrayList<>();
            for(boolean normal:List.of(true,false)) futures.add(pool.submit(()->{
                try{return transacao().execute(st->{produtos.findByIdForUpdate(normal?a.getId():b.getId());ready.countDown();aguardar(ready);produtos.findByIdForUpdate(normal?b.getId():a.getId());return "ok";});}
                catch(PessimisticLockingFailureException e){return e;}
            }));
            int falhas=0;
            for(var f:futures) {
                Object value=f.get(20,TimeUnit.SECONDS);
                if(value instanceof PessimisticLockingFailureException failure) {
                    falhas++;boolean codigo=false;for(Throwable t=failure;t!=null;t=t.getCause()) if(t instanceof SQLException sql && sql.getErrorCode()==1213) codigo=true;
                    assertTrue(codigo,"Deve ser deadlock MySQL 1213, nao timeout");
                }
            }
            assertEquals(1,falhas);assertEquals(antes,snapshot());
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void P_administracaoDaMesaVersusAbertura(boolean desativar) throws Exception {
        var r=concorrer("mesa",idMesa(1),List.of(()->abrir(1,a,1),()->{
            if(desativar)mesaService.alterarAtivacao(1,false);else mesaService.atualizarStatus(1,StatusMesa.MANUTENCAO);return "administrado";
        }));
        assertEquals(1,sucessos(r));
        var m=mesas.findByNumero(1).orElseThrow();
        if(pedidos.count()==1){assertEquals(StatusMesa.OCUPADA,m.getStatus());assertTrue(m.isAtivo());assertEquals(9,saldo(a));}
        else{assertEquals(desativar?StatusMesa.LIVRE:StatusMesa.MANUTENCAO,m.getStatus());assertEquals(!desativar,m.isAtivo());assertEquals(10,saldo(a));}
    }
    @Test void Q_itemDaCozinhaVersusAdicaoNaoPerdeAtualizacao() throws Exception {
        var p=abrir(1,a,1);long item=p.itens().get(0).id();
        var r=concorrer("mesa",idMesa(1),List.of(()->{service.atualizarStatusItem(item,StatusItemPedido.PRONTO);return "pronto";},()->service.adicionarItens(p.id(),itens(b,1))));
        assertEquals(2,sucessos(r));assertEquals("PRONTO",jdbc.queryForObject("select status from item_pedido where id=?",String.class,item));
        assertEquals(new BigDecimal("80.00"),pedidos.findById(p.id()).orElseThrow().getFaturamento());assertEquals(9,saldo(a));assertEquals(9,saldo(b));
    }
    static Stream<Arguments> transicoes4c() {
        return Stream.of(StatusPedido.values()).flatMap(p->Stream.of(StatusItemPedido.values()).flatMap(a->Stream.of(StatusItemPedido.values()).map(n->Arguments.of(p,a,n))));
    }
    @ParameterizedTest @MethodSource("transicoes4c")
    void R_transicoesItens(StatusPedido pedido,StatusItemPedido atual,StatusItemPedido destino) {
        var p=abrir(1,a,1);long item=p.itens().get(0).id();
        jdbc.update("update pedido set status=? where id=?",pedido.name(),p.id());
        if(pedido==StatusPedido.FINALIZADO || pedido==StatusPedido.CANCELADO)jdbc.update("update mesa set status='LIVRE' where numero=1");
        var historico=LocalDateTime.of(2026,1,1,12,0);
        jdbc.update("update item_pedido set status=?,data_hora_conclusao=? where id=?",atual.name(),atual==StatusItemPedido.PRONTO?historico:null,item);
        var antes=snapshot();SQL.clear();
        boolean ativo=pedido==StatusPedido.ABERTO || pedido==StatusPedido.AGUARDANDO_PAGAMENTO;
        boolean permitido=ativo && (atual==destino || atual==StatusItemPedido.PENDENTE || (atual==StatusItemPedido.EM_PREPARO && destino==StatusItemPedido.PRONTO));
        if(!permitido){assertThrows(ConflitoException.class,()->service.atualizarStatusItem(item,destino));assertEquals(antes,snapshot());}
        else {
            service.atualizarStatusItem(item,destino);
            assertEquals(destino.name(),jdbc.queryForObject("select status from item_pedido where id=?",String.class,item));
            if(atual==destino){assertEquals(antes,snapshot());assertTrue(SQL.stream().noneMatch(q->q.toLowerCase().startsWith("update ")));}
            else if(destino==StatusItemPedido.PRONTO)assertNotNull(jdbc.queryForObject("select data_hora_conclusao from item_pedido where id=?",LocalDateTime.class,item));
            else assertNull(jdbc.queryForObject("select data_hora_conclusao from item_pedido where id=?",LocalDateTime.class,item));
        }
    }
    @Test void S_repeticaoConcorrenteProntoNaoReescreveData() throws Exception {
        var p=abrir(1,a,1);long item=p.itens().get(0).id();SQL.clear();
        var inicio=LocalDateTime.now().minusSeconds(1);
        var r=concorrer("mesa",idMesa(1),List.of(()->{service.atualizarStatusItem(item,StatusItemPedido.PRONTO);return "ok";},()->{service.atualizarStatusItem(item,StatusItemPedido.PRONTO);return "ok";}));
        assertEquals(2,sucessos(r));assertEquals(1,SQL.stream().filter(q->q.toLowerCase().startsWith("update item_pedido ")).count());
        var data=jdbc.queryForObject("select data_hora_conclusao from item_pedido where id=?",LocalDateTime.class,item);
        assertNotNull(data);assertFalse(data.isBefore(inicio));assertFalse(data.isAfter(LocalDateTime.now().plusSeconds(1)));
        service.atualizarStatusItem(item,StatusItemPedido.PRONTO);
        assertEquals(data,jdbc.queryForObject("select data_hora_conclusao from item_pedido where id=?",LocalDateTime.class,item));
    }
    @Test void T_atualizacoesConcorrentesNaoRegridem() throws Exception {
        var p=abrir(1,a,1);long item=p.itens().get(0).id();
        var r=concorrer("mesa",idMesa(1),List.of(()->{service.atualizarStatusItem(item,StatusItemPedido.PRONTO);return "ok";},()->{service.atualizarStatusItem(item,StatusItemPedido.EM_PREPARO);return "ok";}));
        assertTrue(sucessos(r)>=1);assertEquals("PRONTO",jdbc.queryForObject("select status from item_pedido where id=?",String.class,item));
        var antes=snapshot();
        var invalidas=concorrer("mesa",idMesa(1),List.of(()->{service.atualizarStatusItem(item,StatusItemPedido.PENDENTE);return "ok";},()->{service.atualizarStatusItem(item,StatusItemPedido.EM_PREPARO);return "ok";}));
        assertEquals(0,sucessos(invalidas));assertEquals(antes,snapshot());
    }
    @Test void U_pagamentoVersusPreparoRespeitaEstadoAtual() throws Exception {
        var p=abrir(1,a,1);long item=p.itens().get(0).id();
        jdbc.update("update pedido set status='AGUARDANDO_PAGAMENTO' where id=?",p.id());
        var r=concorrer("mesa",idMesa(1),List.of(()->service.confirmarPagamentoEFecharMesa(p.id()),()->{service.atualizarStatusItem(item,StatusItemPedido.PRONTO);return "ok";}));
        assertTrue(sucessos(r)==1 || sucessos(r)==2);
        assertEquals(sucessos(r)==2?StatusPedido.FINALIZADO:StatusPedido.AGUARDANDO_PAGAMENTO,pedidos.findById(p.id()).orElseThrow().getStatus());
        assertEquals(sucessos(r)==2?StatusMesa.LIVRE:StatusMesa.OCUPADA,mesas.findByNumero(1).orElseThrow().getStatus());
        assertEquals("PRONTO",jdbc.queryForObject("select status from item_pedido where id=?",String.class,item));
    }
    @ParameterizedTest @EnumSource(StatusPedido.class)
    void V_exclusaoComHistoricoPreservaDados(StatusPedido estado) throws Exception {
        var p=abrir(1,a,1);jdbc.update("update pedido set status=? where id=?",estado.name(),p.id());
        if(estado==StatusPedido.FINALIZADO || estado==StatusPedido.CANCELADO)jdbc.update("update mesa set status='LIVRE' where numero=1");
        credencial("tablet-personalizado",1);var antes=snapshot();
        var mvc=MockMvcBuilders.standaloneSetup(new MesaController(mesaService)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(delete("/mesas/1")).andExpect(status().isConflict()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        assertEquals(antes,snapshot());
        jdbc.update("update mesa set ativo=false where numero=1");antes=snapshot();
        mvc.perform(delete("/mesas/1")).andExpect(status().isConflict());assertEquals(antes,snapshot());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void W_exclusaoValidaUsaVinculoReal(boolean possuiCredencial) throws Exception {
        if(possuiCredencial)credencial("tablet-personalizado",1);credencial("outra-mesa",2);
        var homonimo=new Usuario();homonimo.setUsername("mesa1");homonimo.setSenha("hash-sintetico");homonimo.setRole(RoleName.GERENTE);usuarios.saveAndFlush(homonimo);
        var mvc=MockMvcBuilders.standaloneSetup(new MesaController(mesaService)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(delete("/mesas/1")).andExpect(status().isNoContent());
        assertTrue(mesas.findByNumero(1).isEmpty());assertTrue(usuarios.findByUsername("tablet-personalizado").isEmpty());
        assertEquals(homonimo.getId(),usuarios.findByUsername("mesa1").orElseThrow().getId());assertEquals(2,usuarios.findByUsername("outra-mesa").orElseThrow().getMesa().getNumero());
        assertEquals(10,saldo(a));assertEquals(0,pedidos.count());
    }
    @Test void X_conflitoFkDesconhecidoReverteCredencialEMesa() throws Exception {
        credencial("tablet-personalizado",1);
        jdbc.execute("create table dependencia_mesa_4c (id bigint primary key,mesa_id bigint not null,foreign key(mesa_id) references mesa(id)) engine=InnoDB");
        try {
            jdbc.update("insert into dependencia_mesa_4c values(1,?)",idMesa(1));var antes=snapshot();
            var mvc=MockMvcBuilders.standaloneSetup(new MesaController(mesaService)).setControllerAdvice(new ApiExceptionHandler()).build();
            mvc.perform(delete("/mesas/1")).andExpect(status().isConflict()).andExpect(jsonPath("$.mensagem").isNotEmpty());
            assertEquals(antes,snapshot());assertTrue(usuarios.findByUsername("tablet-personalizado").isPresent());
        } finally {jdbc.execute("drop table dependencia_mesa_4c");}
    }
    @Test void Y_exclusaoVersusAbertura() throws Exception {
        credencial("tablet-personalizado",1);
        var r=concorrer("mesa",idMesa(1),List.of(()->{mesaService.excluir(1);return "excluida";},()->abrir(1,a,1)));
        assertEquals(1,sucessos(r));
        if(mesas.findByNumero(1).isPresent()) {
            assertEquals(1,pedidos.count());assertEquals(9,saldo(a));assertTrue(usuarios.findByUsername("tablet-personalizado").isPresent());
            assertEquals(StatusMesa.OCUPADA,mesas.findByNumero(1).orElseThrow().getStatus());
        } else {assertEquals(0,pedidos.count());assertEquals(10,saldo(a));assertTrue(usuarios.findByUsername("tablet-personalizado").isEmpty());}
    }
    @Test void Z_historicoIncoerenteNaoEReescrito() {
        var p=abrir(1,a,1);long item=p.itens().get(0).id();
        jdbc.update("update item_pedido set data_hora_conclusao='2026-01-01 12:00:00' where id=?",item);var antes=snapshot();
        assertThrows(ConflitoException.class,()->service.atualizarStatusItem(item,StatusItemPedido.PRONTO));assertEquals(antes,snapshot());
        jdbc.update("update item_pedido set status='PRONTO',data_hora_conclusao=null where id=?",item);antes=snapshot();
        service.atualizarStatusItem(item,StatusItemPedido.PRONTO);assertEquals(antes,snapshot());
    }
    @Test void Z2_propostaD20ValidaSomenteSchemaEmBancoDescartavel() throws Exception {
        credencial("autor-proposta",1);credencial("outro-autor",2);var p=abrir(1,a,1);
        long autor=usuarios.findByUsername("autor-proposta").orElseThrow().getId();
        long outro=usuarios.findByUsername("outro-autor").orElseThrow().getId();var antes=snapshot();
        String ddl=java.nio.file.Files.readString(Path.of("..","Dados_SQL","fase4c","proposta_idempotencia.sql"));
        jdbc.execute("drop table idempotencia_adicao"); // Agora mapeada pelo Hibernate; ensaia o DDL aprovado.
        jdbc.execute(ddl);
        try {
            assertEquals(2,jdbc.queryForObject("select count(*) from information_schema.referential_constraints where constraint_schema=database() and table_name='idempotencia_adicao'",Integer.class));
            assertEquals(1,jdbc.queryForObject("select count(*) from information_schema.table_constraints where constraint_schema=database() and table_name='idempotencia_adicao' and constraint_name='uk_idempotencia_adicao'",Integer.class));
            try(var c=ds.getConnection()) {
                c.setAutoCommit(false);
                try(var st=c.prepareStatement("insert into idempotencia_adicao(usuario_id,pedido_id,chave,payload_hash,resposta_json,status_http,criado_em,expira_em) values(?,?,?,?,?,200,?,?)")) {
                    st.setLong(1,autor);st.setLong(2,p.id());st.setBytes(3,new byte[16]);st.setBytes(4,new byte[32]);st.setString(5,"{\"id\":1}");
                    var data=LocalDateTime.of(2026,1,1,0,0);st.setTimestamp(6,Timestamp.valueOf(data));st.setTimestamp(7,Timestamp.valueOf(data.plusDays(7)));
                    assertEquals(1,st.executeUpdate());assertEquals(1062,assertThrows(SQLException.class,st::executeUpdate).getErrorCode());
                    st.setLong(1,outro);assertEquals(1,st.executeUpdate());
                    st.setLong(1,-1);assertEquals(1452,assertThrows(SQLException.class,st::executeUpdate).getErrorCode());
                    st.setLong(1,autor);st.setBytes(3,null);assertEquals(1048,assertThrows(SQLException.class,st::executeUpdate).getErrorCode());
                    byte[] outraChave=new byte[16];outraChave[0]=1;st.setBytes(3,outraChave);
                    st.setTimestamp(7,Timestamp.valueOf(data.minusDays(1)));assertEquals(3819,assertThrows(SQLException.class,st::executeUpdate).getErrorCode());
                    st.setTimestamp(7,Timestamp.valueOf(data.plusDays(7)));st.setLong(2,-1);
                    assertEquals(1452,assertThrows(SQLException.class,st::executeUpdate).getErrorCode());
                    assertEquals(3819,assertThrows(SQLException.class,()->c.createStatement().executeUpdate("insert into idempotencia_adicao(usuario_id,pedido_id,chave,payload_hash,resposta_json,status_http,criado_em,expira_em) select usuario_id,pedido_id,unhex('02000000000000000000000000000000'),payload_hash,resposta_json,201,criado_em,expira_em from idempotencia_adicao limit 1")).getErrorCode());
                    c.createStatement().executeUpdate("update produto set qtd_estoque=qtd_estoque-1 where id="+a.getId());
                    c.rollback();
                }
            }
            assertEquals(0,jdbc.queryForObject("select count(*) from idempotencia_adicao",Integer.class));assertEquals(antes,snapshot());
        } finally {jdbc.update("delete from idempotencia_adicao");}
    }
    private void credencial(String nome,int numero) {
        var u=new Usuario();u.setUsername(nome);u.setSenha("hash-sintetico");u.setRole(RoleName.MESA);u.setMesa(mesas.findByNumero(numero).orElseThrow());usuarios.saveAndFlush(u);
    }
    private List<Object> concorrer(String tabela,long id,List<Callable<Object>> tarefas) throws Exception {
        var pool=Executors.newFixedThreadPool(tarefas.size());var ready=new CountDownLatch(tarefas.size());var start=new CountDownLatch(1);
        try(var holder=ds.getConnection()) {
            holder.setAutoCommit(false);holder.createStatement().executeQuery("select id from "+tabela+" where id="+id+" for update").close();
            long owner;try(var rs=holder.createStatement().executeQuery("select connection_id()")){rs.next();owner=rs.getLong(1);}
            List<Future<Object>> futures=new ArrayList<>();
            for(var task:tarefas) futures.add(pool.submit(()->{ready.countDown();aguardar(start);try{return task.call();}catch(ConflitoException e){return e;}catch(RecursoNaoEncontradoException e){return e;}catch(ResponseStatusException e){if(e.getStatusCode().value()!=400)throw e;return e;}}));
            assertTrue(ready.await(8,TimeUnit.SECONDS));start.countDown();
            long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);int waits=0;
            while(System.nanoTime()<limit) {
                waits=jdbc.queryForObject("select count(distinct w.requesting_thread_id) from performance_schema.data_lock_waits w join performance_schema.threads t on t.thread_id=w.requesting_thread_id where t.processlist_db=database()",Integer.class);
                if(waits>=tarefas.size()) break;LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            }
            assertTrue(waits>=tarefas.size(),"As operacoes devem estar simultaneamente aguardando locks reais");
            if(tabela.equals("produto")) {
                assertEquals(0,jdbc.queryForObject("select count(*) from performance_schema.data_locks l join performance_schema.threads t on t.thread_id=l.thread_id where l.object_schema=database() and l.object_name='produto' and l.lock_type='RECORD' and l.lock_status='GRANTED' and t.processlist_id<>?",Integer.class,owner),"Nenhum produto posterior deve ser bloqueado antes do primeiro ID");
            }
            holder.commit();List<Object> resultados=new ArrayList<>();for(var f:futures)resultados.add(f.get(20,TimeUnit.SECONDS));
            assertTrue(SQL.stream().anyMatch(s->s.toLowerCase().contains("for update")));
            System.out.println("MYSQL_CONCORRENCIA tabela="+tabela+" operacoes="+tarefas.size()+" esperas_reais="+waits+" confirmadas="+sucessos(resultados));
            return resultados;
        } finally {start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    private static void aguardar(CountDownLatch l) {try{if(!l.await(12,TimeUnit.SECONDS))throw new IllegalStateException("Timeout de sincronizacao");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    private long sucessos(List<Object> r){return r.stream().filter(v->!(v instanceof ConflitoException) && !(v instanceof ResponseStatusException) && !(v instanceof RecursoNaoEncontradoException)).count();}
    private Produto produto(String nome,int preco,int estoque){var p=new Produto();p.setNome(nome);p.setCategoria(Categoria.PIZZAS);p.setAtivo(true);p.setPrecoBase(BigDecimal.valueOf(preco));p.setQtdEstoque(estoque);p.setDescricao(new DetalhesDescricao());return produtos.save(p);}
    private void estoque(Produto p,int valor){transacao().executeWithoutResult(s->jdbc.update("update produto set qtd_estoque=? where id=?",valor,p.getId()));}
    private long idMesa(int n){return mesas.findByNumero(n).orElseThrow().getId();}
    private int saldo(Produto p){return jdbc.queryForObject("select qtd_estoque from produto where id=?",Integer.class,p.getId());}
    private int quantidade(Produto p){return jdbc.queryForObject("select coalesce(sum(quantidade),0) from item_pedido where produto_id=?",Integer.class,p.getId());}
    private List<ItemPedidoRequestDTO> itens(Produto p,int n){return List.of(new ItemPedidoRequestDTO(p.getId(),n));}
    private PedidoResponseDTO abrir(int mesa,Produto p,int n){return service.abrirPedido(new PedidoRequestDTO(mesa,itens(p,n),null));}
    private TransactionTemplate transacao(){var t=new TransactionTemplate(tm);t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return t;}
    private List<List<Map<String,Object>>> snapshot(){var r=new ArrayList<List<Map<String,Object>>>();for(String t:List.of("mesa","pedido","produto","item_pedido","usuario","produto_ingredientes"))r.add(jdbc.queryForList("select * from "+t+" order by "+(t.equals("produto_ingredientes")?"produto_id,ingrediente":"id")));return r;}
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=PedidoRepository.class)
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            String url=System.getenv("TEST_MYSQL_URL");
            if(url==null || !url.startsWith("jdbc:mysql://127.0.0.1:") || url.contains(":3306/") || url.contains("/pizzamaua")) throw new IllegalStateException("Destino de teste proibido");
            var d=new DriverManagerDataSource(url,"root",System.getenv("TEST_MYSQL_PASSWORD"));
            try(var c=d.getConnection();var s=c.createStatement();var r=s.executeQuery("select @@server_uuid,@@port,@@datadir,version(),database()")) {
                if(!r.next() || !r.getString(1).equals(System.getenv("TEST_MYSQL_UUID")) || r.getInt(2)==3306
                        || !Path.of(r.getString(3)).normalize().equals(Path.of(System.getenv("TEST_MYSQL_DATADIR")).normalize())
                        || !r.getString(4).equals("8.0.42") || !r.getString(5).equals("fase4b_sintetica")) throw new IllegalStateException("Isolamento nao comprovado");
            }
            return d;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("inf.anibele.pizzariamauabackend.model");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl","hibernate.session_factory.statement_inspector",(StatementInspector)sql->{SQL.add(sql);if(sql.toLowerCase().contains("for update")) LOCK_SQL.add(sql);return sql;}));return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf){return new JpaTransactionManager(emf);}
        @Bean tools.jackson.databind.json.JsonMapper jsonMapper() { return tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(); }
        @Bean PedidoService pedidoService(PedidoRepository p,MesaRepository m,ProdutoRepository pr,ItemPedidoRepository i){return new PedidoService(p,m,pr,i,new PedidoMapper(new ItemPedidoMapper()),mock(AutorizacaoMesa.class));}
        @Bean ProdutoService produtoService(ProdutoRepository p){return new ProdutoService(p,new ProdutoMapper());}
        @Bean MesaService mesaService(MesaRepository m,UsuarioRepository u,PedidoRepository p){return new MesaService(m,u,mock(PasswordEncoder.class),new MesaMapper(),mock(AutorizacaoMesa.class),p);}
    }
}

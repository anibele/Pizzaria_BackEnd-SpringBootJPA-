package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.dashboarddto.*;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.*;
import inf.anibele.pizzariamauabackend.mapper.*;
import inf.anibele.pizzariamauabackend.security.AutorizacaoMesa;
import inf.anibele.pizzariamauabackend.controller.*;
import inf.anibele.pizzariamauabackend.exception.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="TEST_MYSQL_URL",matches="jdbc:mysql://127[.]0[.]0[.]1:.*")
@SpringJUnitConfig(FinanceiroMysqlTest.Config.class)
class FinanceiroMysqlTest {
    static final LocalDate DIA=LocalDate.of(2026,11,17);
    @Autowired DashboardService dashboard;
    @Autowired PedidoService service;
    @Autowired PedidoRepository pedidos;
    @Autowired MesaRepository mesas;
    @Autowired ProdutoRepository produtos;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager tm;
    @Autowired DataSource ds;
    JdbcTemplate jdbc;int numero;
    @BeforeEach void limpar() {
        jdbc=new JdbcTemplate(ds);numero=0;
        for(String t:List.of("idempotencia_adicao","item_pedido","pedido","usuario","produto_ingredientes","produto","mesa"))jdbc.update("delete from "+t);
    }
    @AfterEach void integridade() {
        assertEquals(0,jdbc.queryForObject("select count(*) from pedido p where p.faturamento<>(select coalesce(sum(i.quantidade*i.preco_unitario),0) from item_pedido i where i.pedido_id=p.id)",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from produto where qtd_estoque<0",Integer.class));
    }
    private Pedido pedido(StatusPedido status,LocalDateTime data,String forma,String... precos) {
        var m=new Mesa();m.setNumero(++numero);m.setAtivo(true);m.setStatus(status==StatusPedido.ABERTO || status==StatusPedido.AGUARDANDO_PAGAMENTO?StatusMesa.OCUPADA:StatusMesa.LIVRE);m=mesas.save(m);
        var p=new Pedido();p.setMesa(m);p.setDataHora(data);p.setStatus(status);p.setFormaPagamento(forma);if(status==StatusPedido.FINALIZADO && data!=null)p.setDataPagamentoUtc(data.plusHours(3));p.setItens(new ArrayList<>());p.setFaturamento(BigDecimal.ZERO);
        for(String preco:precos){var pr=new Produto();pr.setNome("Produto "+numero+"-"+p.getItens().size());pr.setPrecoBase(new BigDecimal(preco));pr.setCategoria(Categoria.PIZZAS);pr.setAtivo(true);pr.setQtdEstoque(50);pr.setDescricao(new DetalhesDescricao());pr=produtos.save(pr);
            var item=new ItemPedido();item.setPedido(p);item.setProduto(pr);item.setQuantidade(1);item.setPrecoUnitario(pr.getPrecoBase());item.setStatus(StatusItemPedido.PENDENTE);p.getItens().add(item);p.setFaturamento(p.getFaturamento().add(pr.getPrecoBase()));}
        return pedidos.saveAndFlush(p);
    }
    private Pedido pedido(StatusPedido s,String forma,String... valores){return pedido(s,DIA.atTime(12,0),forma,valores);}
    private void valor(String esperado,BigDecimal observado){assertEquals(0,new BigDecimal(esperado).compareTo(observado));}
    private PedidoRepository.ResumoFinanceiro resumo(LocalDate dia){return pedidos.calcularResumoFinanceiro(dia.atTime(3,0),dia.plusDays(1).atTime(3,0));}
    @ParameterizedTest @EnumSource(StatusPedido.class)
    void elegibilidadePorEstado(StatusPedido status) throws Exception {
        pedido(status,"PIX","10.25");String before=snapshot();var r=dashboard.obterResumoDoDia();valor(status==StatusPedido.FINALIZADO?"10.25":"0",r.faturamento());valor(status==StatusPedido.FINALIZADO?"10.25":"0",r.ticketMedio());
        assertEquals(status==StatusPedido.FINALIZADO?1L:0L,resumo(DIA).getQuantidade());assertEquals(status==StatusPedido.FINALIZADO?1:0,dashboard.obterPagamentosDoDia().size());assertEquals(before,snapshot());
    }
    @Test void misturaCentavosMultiplosItensTicketEGruposCoerentes() throws Exception {
        pedido(StatusPedido.ABERTO,"PIX","100.99");pedido(StatusPedido.AGUARDANDO_PAGAMENTO,"CARTAO","200.99");pedido(StatusPedido.CANCELADO,"DINHEIRO","300.99");
        pedido(StatusPedido.FINALIZADO,"PIX","5.05","15.25");pedido(StatusPedido.FINALIZADO,"CARTAO","10.01");String before=snapshot();
        var r=dashboard.obterResumoDoDia();valor("30.31",r.faturamento());valor("15.16",r.ticketMedio());assertEquals(2L,resumo(DIA).getQuantidade());
        assertEquals(2L,dashboard.obterPagamentosDoDia().stream().mapToLong(PagamentoDTO::quantidade).sum());valor("30.31",service.obterFaturamentoPorData(DIA));assertEquals(2,service.listarPedidosFinalizadosPorData(DIA).size());assertEquals(before,snapshot());
    }
    @Test void vazioRetornaZeros() {
        valor("0",dashboard.obterResumoDoDia().faturamento());valor("0",dashboard.obterResumoDoDia().ticketMedio());assertTrue(dashboard.obterPagamentosDoDia().isEmpty());valor("0",service.obterFaturamentoPorData(DIA));assertTrue(service.listarPedidosFinalizadosPorData(DIA).isEmpty());
    }
    @Test void todosPendentesNaoGeramReceita() {pedido(StatusPedido.ABERTO,(String)null,"10.01");pedido(StatusPedido.AGUARDANDO_PAGAMENTO,"PIX","20.02");valor("0",dashboard.obterResumoDoDia().faturamento());assertEquals(0L,resumo(DIA).getQuantidade());assertTrue(dashboard.obterPagamentosDoDia().isEmpty());}
    @Test void formaAusenteEVazioLegadoNaoSaoDinheiro() throws Exception {
        pedido(StatusPedido.FINALIZADO,(String)null,"1.01");pedido(StatusPedido.FINALIZADO,"DINHEIRO","2.02");pedido(StatusPedido.FINALIZADO,"","3.03");pedido(StatusPedido.FINALIZADO,"LEGADO","4.04");String before=snapshot();
        var grupos=dashboard.obterPagamentosDoDia();assertEquals(4,grupos.size());assertEquals(1L,grupos.stream().filter(g->g.formaPagamento()==null).findFirst().orElseThrow().quantidade());assertEquals(1L,grupos.stream().filter(g->"DINHEIRO".equals(g.formaPagamento())).findFirst().orElseThrow().quantidade());assertEquals(before,snapshot());
    }
    @Test void bebidasContinuamUsandoDenominadorOperacional() {
        var aberto=pedido(StatusPedido.ABERTO,(String)null,"4.00");jdbc.update("update produto set categoria='BEBIDAS' where id=(select produto_id from item_pedido where pedido_id=? limit 1)",aberto.getId());
        pedido(StatusPedido.FINALIZADO,"PIX","10.00");pedido(StatusPedido.FINALIZADO,"PIX","20.00");assertEquals(100.0/3,dashboard.obterResumoDoDia().taxaBebidas(),0.000001);valor("15.00",dashboard.obterResumoDoDia().ticketMedio());
    }
    @ParameterizedTest @ValueSource(strings={"2026-11-18","2026-12-01","2027-01-01"})
    void intervalosAdjacentesDiaMesAnoComMicros(String inicioProximo) {
        LocalDate dia=LocalDate.parse(inicioProximo).minusDays(1);var inicio=dia.atStartOfDay();var fim=dia.plusDays(1).atStartOfDay();
        pedido(StatusPedido.FINALIZADO,inicio.minusNanos(1000),"PIX","1.11");pedido(StatusPedido.FINALIZADO,inicio,"PIX","2.22");pedido(StatusPedido.FINALIZADO,fim.minusNanos(1000),"PIX","3.33");pedido(StatusPedido.FINALIZADO,fim,"PIX","4.44");
        valor("5.55",resumo(dia).getFaturamento());assertEquals(2L,resumo(dia).getQuantidade());valor("4.44",resumo(dia.plusDays(1)).getFaturamento());valor("1.11",resumo(dia.minusDays(1)).getFaturamento());
        valor("5.55",service.obterFaturamentoPorData(dia));assertEquals(2,service.listarPedidosFinalizadosPorData(dia).size());
    }
    @Test void timestampAusenteNaoRecebeDiaInventado() {pedido(StatusPedido.FINALIZADO,(LocalDateTime)null,"PIX","10.00");valor("0",resumo(DIA).getFaturamento());assertNull(pedidos.findAll().get(0).getDataHora());}
    @Test void pagamentoRealSomenteAposConfirmacao() throws Exception {
        var p=pedido(StatusPedido.ABERTO,(String)null,"10.25","20.10");valor("0",dashboard.obterResumoDoDia().faturamento());service.finalizarPedido(p.getId()," pix ");
        valor("0",dashboard.obterResumoDoDia().faturamento());assertTrue(dashboard.obterPagamentosDoDia().isEmpty());service.confirmarPagamentoEFecharMesa(p.getId());
        valor("30.35",dashboard.obterResumoDoDia().faturamento());valor("30.35",dashboard.obterResumoDoDia().ticketMedio());assertEquals(1L,resumo(DIA).getQuantidade());assertEquals("PIX",pedidos.findById(p.getId()).orElseThrow().getFormaPagamento());assertEquals(StatusMesa.LIVRE,mesas.findByNumero(1).orElseThrow().getStatus());
        String before=snapshot();assertThrows(ConflitoException.class,()->service.confirmarPagamentoEFecharMesa(p.getId()));assertEquals(before,snapshot());
    }
    @ParameterizedTest @ValueSource(strings={" pix ","cartao","DINHEIRO"})
    void formaPermitidaNormalizadaSemAlterarAbertura(String forma) {
        var p=pedido(StatusPedido.ABERTO,(String)null,"10.00");service.finalizarPedido(p.getId(),forma);assertEquals(forma.strip().toUpperCase(Locale.ROOT),pedidos.findById(p.getId()).orElseThrow().getFormaPagamento());assertEquals(DIA.atTime(12,0),pedidos.findById(p.getId()).orElseThrow().getDataHora());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={" ","BOLETO","PENDENTE","credito","PIX,CARTAO"})
    void formaInvalida400SemAlteracao(String forma) throws Exception {
        var p=pedido(StatusPedido.ABERTO,(String)null,"10.00");String before=snapshot();var ex=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.finalizarPedido(p.getId(),forma));assertEquals(400,ex.getStatusCode().value());assertEquals(before,snapshot());
    }
    @Test void pagamentoNoDiaSeguinteUsaRecebimentoPreservandoAbertura() {
        var ontem=DIA.minusDays(1).atTime(23,40);var p=pedido(StatusPedido.AGUARDANDO_PAGAMENTO,ontem,"PIX","10.25");service.confirmarPagamentoEFecharMesa(p.getId());
        valor("10.25",resumo(DIA).getFaturamento());valor("0",resumo(DIA.minusDays(1)).getFaturamento());assertEquals(ontem,pedidos.findById(p.getId()).orElseThrow().getDataHora()); // Recebimento independe da abertura.
    }
    @Test void leituraNaoVePagamentoNaoConfirmadoNoCommit() throws Exception {
        var p=pedido(StatusPedido.AGUARDANDO_PAGAMENTO,"PIX","12.34");var escrito=new CountDownLatch(1);var liberar=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try {
            var future=pool.submit(()->{var tx=new TransactionTemplate(tm);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.executeWithoutResult(s->{service.confirmarPagamentoEFecharMesa(p.getId());em.flush();escrito.countDown();aguardar(liberar);});});
            assertTrue(escrito.await(8,TimeUnit.SECONDS));valor("0",dashboard.obterResumoDoDia().faturamento());assertEquals(0L,resumo(DIA).getQuantidade());
            liberar.countDown();future.get(15,TimeUnit.SECONDS);valor("12.34",dashboard.obterResumoDoDia().faturamento());assertEquals(1L,resumo(DIA).getQuantidade());
        }finally{liberar.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    @Test void contratosHttpPreservadosEEntradaInvalida400() throws Exception {
        var p=pedido(StatusPedido.ABERTO,(String)null,"10.25");var mvc=MockMvcBuilders.standaloneSetup(new DashboardController(dashboard),new PedidoController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(patch("/pedidos/"+p.getId()+"/finalizar").param("formaPagamento","BOLETO")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").exists());
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isOk()).andExpect(jsonPath("$.faturamento").value(0)).andExpect(jsonPath("$.ticketMedio").value(0));
        service.finalizarPedido(p.getId(),"PIX");service.confirmarPagamentoEFecharMesa(p.getId());
        mvc.perform(get("/pedidos/faturamento").param("data",DIA.toString())).andExpect(status().isOk()).andExpect(content().string("10.25"));
        mvc.perform(get("/pedidos/finalizados").param("data",DIA.toString())).andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("FINALIZADO"));
    }
    @Test void propostaDeDataNullableNaoFabricaHistorico() throws Exception {
        pedido(StatusPedido.FINALIZADO,"PIX","9.99");jdbc.update("update pedido set data_pagamento_utc=null");String before=snapshot();
        jdbc.execute("alter table pedido drop column data_pagamento_utc");
        String ddl=Files.readString(Path.of("..","Dados_SQL","fase5a","proposta_data_pagamento.sql"));
        jdbc.execute(ddl);
        try {
            assertEquals(1,jdbc.queryForObject("select count(*) from pedido where data_pagamento_utc is null",Integer.class));
            assertEquals("YES",jdbc.queryForObject("select is_nullable from information_schema.columns where table_schema=database() and table_name='pedido' and column_name='data_pagamento_utc'",String.class));
            assertNull(jdbc.queryForObject("select column_default from information_schema.columns where table_schema=database() and table_name='pedido' and column_name='data_pagamento_utc'",String.class));
            // Valida o modelo atual mesmo com coluna extra ainda nao mapeada.
            var validation=Config.factory(ds,"validate");validation.afterPropertiesSet();validation.destroy();
        }finally{ /* Coluna migrada continua mapeada. */ }
        assertEquals(before,snapshot());
    }
    @Test void confirmacaoRegistraUtcComMicrosSemSobrescrever() throws Exception {
        var p=pedido(StatusPedido.AGUARDANDO_PAGAMENTO,"PIX","15.25");
        assertNull(pedidos.findById(p.getId()).orElseThrow().getDataPagamentoUtc());
        service.confirmarPagamentoEFecharMesa(p.getId());
        var pago=pedidos.findById(p.getId()).orElseThrow();
        assertEquals(LocalDateTime.of(2026,11,17,15,0,0,123456000),pago.getDataPagamentoUtc());
        try(var c=ds.getConnection();var st=c.createStatement()) {
            st.execute("set time_zone='+09:00'");
            try(var r=st.executeQuery("select data_pagamento_utc from pedido where id="+p.getId())) {
                assertTrue(r.next());assertEquals(pago.getDataPagamentoUtc(),r.getObject(1,LocalDateTime.class));
            }
        }
        String antes=snapshot();assertThrows(ConflitoException.class,()->service.confirmarPagamentoEFecharMesa(p.getId()));assertEquals(antes,snapshot());
    }
    @ParameterizedTest @EnumSource(value=StatusPedido.class,names={"ABERTO","CANCELADO"})
    void confirmacaoRejeitadaNaoDataNemModifica(StatusPedido status) throws Exception {
        var p=pedido(status,"PIX","1.25");String antes=snapshot();
        assertThrows(ConflitoException.class,()->service.confirmarPagamentoEFecharMesa(p.getId()));
        assertNull(pedidos.findById(p.getId()).orElseThrow().getDataPagamentoUtc());assertEquals(antes,snapshot());
    }
    @Test void rollbackAposFlushReverteTimestampFinalizacaoEMesa() throws Exception {
        var p=pedido(StatusPedido.AGUARDANDO_PAGAMENTO,"PIX","2.25");String antes=snapshot();
        var tx=new TransactionTemplate(tm);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThrows(ConflitoException.class,()->tx.executeWithoutResult(st->{service.confirmarPagamentoEFecharMesa(p.getId());em.flush();throw new ConflitoException("Falha controlada");}));
        assertEquals(antes,snapshot());assertNull(pedidos.findById(p.getId()).orElseThrow().getDataPagamentoUtc());
    }
    @Test void pendenteComDataInconsistenteNaoSobrescreve() throws Exception {
        var p=pedido(StatusPedido.AGUARDANDO_PAGAMENTO,"PIX","2.25");
        jdbc.update("update pedido set data_pagamento_utc='2026-11-16 10:00:00.111111' where id=?",p.getId());
        String antes=snapshot();assertThrows(ConflitoException.class,()->service.confirmarPagamentoEFecharMesa(p.getId()));assertEquals(antes,snapshot());
    }
    @Test void historicoPagoSemDataPreservadoForaDosPeriodos() throws Exception {
        var p=pedido(StatusPedido.FINALIZADO,"PIX","12.34");jdbc.update("update pedido set data_pagamento_utc=null where id=?",p.getId());
        String antes=snapshot();valor("0",dashboard.obterResumoDoDia().faturamento());assertTrue(service.listarPedidosFinalizadosPorData(DIA).isEmpty());assertTrue(dashboard.obterPagamentosDoDia().isEmpty());
        valor("12.34",jdbc.queryForObject("select sum(faturamento) from pedido where status='FINALIZADO'",BigDecimal.class));assertEquals(antes,snapshot());
    }
    @ParameterizedTest @ValueSource(strings={"2026-11-18","2026-12-01","2027-01-01"})
    void recebimentoNoLimiteCivilIndependeDaAbertura(String proximo) {
        var dia=LocalDate.parse(proximo).minusDays(1);var periodo=PeriodoRecebimento.doDia(dia);
        var datas=List.of(periodo.inicioUtc().minusNanos(1000),periodo.inicioUtc(),periodo.fimUtc().minusNanos(1000),periodo.fimUtc());
        for(int n=0;n<4;n++) {var p=pedido(StatusPedido.FINALIZADO,dia.minusDays(5).atTime(12,0),"PIX","1.25");jdbc.update("update pedido set data_pagamento_utc=? where id=?",datas.get(n),p.getId());}
        valor("2.50",service.obterFaturamentoPorData(dia));assertEquals(2,service.listarPedidosFinalizadosPorData(dia).size());
        valor("1.25",service.obterFaturamentoPorData(dia.plusDays(1)));valor("1.25",service.obterFaturamentoPorData(dia.minusDays(1)));
    }
    private static void aguardar(CountDownLatch l){try{if(!l.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    private String snapshot() throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        for(String t:List.of("usuario","mesa","pedido","produto","item_pedido","idempotencia_adicao","produto_ingredientes"))for(var row:jdbc.queryForList("select * from "+t+" order by "+(t.equals("produto_ingredientes")?"produto_id,ingrediente":"id")))for(var v:new TreeMap<>(row).values()){
            String text=v instanceof byte[] bytes?HexFormat.of().formatHex(bytes):String.valueOf(v);digest.update((text.length()+":"+text).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        return HexFormat.of().formatHex(digest.digest());
    }
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=PedidoRepository.class)
    static class Config {
        @Bean DataSource dataSource() throws Exception{return new ConcorrenciaMysqlTest.Config().dataSource();}
        static LocalContainerEntityManagerFactoryBean factory(DataSource ds,String ddl){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("inf.anibele.pizzariamauabackend.model");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto",ddl,"hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));return f;}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) throws Exception {
            var bootstrap=factory(ds,"create");bootstrap.afterPropertiesSet();bootstrap.destroy();
            var jdbc=new JdbcTemplate(ds);jdbc.execute("drop table idempotencia_adicao");jdbc.execute(Files.readString(Path.of("..","Dados_SQL","fase4c","proposta_idempotencia.sql")));
            String sql=Files.readString(Path.of("..","Dados_SQL","fase3a","03_restricao_perfil.sql"));jdbc.execute(sql.substring(sql.indexOf("ALTER TABLE usuario")));
            return factory(ds,"validate");
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf){return new JpaTransactionManager(emf);}
        @Bean JsonMapper jsonMapper(){return JsonMapper.builder().findAndAddModules().build();}
        @Bean PedidoService pedidoService(PedidoRepository p,MesaRepository m,ProdutoRepository pr,ItemPedidoRepository i){return new PedidoService(p,m,pr,i,new PedidoMapper(new ItemPedidoMapper()),mock(AutorizacaoMesa.class),Clock.fixed(Instant.parse("2026-11-17T15:00:00.123456789Z"),ZoneOffset.UTC));}
        @Bean DashboardService dashboardService(PedidoRepository p,ItemPedidoRepository i){return new DashboardService(p,i,Clock.fixed(Instant.parse("2026-11-17T15:00:00Z"),ZoneOffset.UTC));}
    }
}

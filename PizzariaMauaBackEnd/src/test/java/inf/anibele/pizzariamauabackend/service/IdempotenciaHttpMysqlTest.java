package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.controller.*;
import inf.anibele.pizzariamauabackend.dto.*;
import inf.anibele.pizzariamauabackend.exception.ApiExceptionHandler;
import inf.anibele.pizzariamauabackend.mapper.*;
import inf.anibele.pizzariamauabackend.model.*;
import inf.anibele.pizzariamauabackend.repository.*;
import inf.anibele.pizzariamauabackend.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import javax.sql.DataSource;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_MYSQL_URL",matches="jdbc:mysql://127[.]0[.]0[.]1:.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IdempotenciaHttpMysqlTest {
    private ConfigurableApplicationContext app,second;
    private JdbcTemplate jdbc;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final String secret=secret();
    private String mesaToken,outroToken,gerenteToken;
    private long pedido,produto,mesaId;
    public static final Queue<String> SQL=new ConcurrentLinkedQueue<>();
    public static class Trace implements StatementInspector {
        public String inspect(String sql){SQL.add(sql);return sql;}
    }
    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration
    @Import({PedidoController.class,AutenticacaoController.class,PedidoService.class,AutenticacaoService.class,
            PedidoMapper.class,ItemPedidoMapper.class,AutorizacaoMesa.class,SecurityConfigurations.class,
            SecurityFilter.class,TokenService.class,ApiExceptionHandler.class})
    @org.springframework.boot.persistence.autoconfigure.EntityScan(basePackageClasses=Pedido.class)
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackageClasses=PedidoRepository.class)
    static class App {
        @Bean DataSource dataSource() throws Exception {return new ConcorrenciaMysqlTest.Config().dataSource();}
    }
    private static String secret(){byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}
    private ConfigurableApplicationContext start(String ddl) {
        var env=new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("isolamento4d",Map.ofEntries(
                Map.entry("spring.profiles.active","test"),Map.entry("server.address","127.0.0.1"),Map.entry("server.port","0"),
                Map.entry("spring.jpa.hibernate.ddl-auto",ddl),Map.entry("spring.jpa.open-in-view","false"),Map.entry("spring.jpa.show-sql","false"),
                Map.entry("spring.sql.init.mode","never"),Map.entry("api.security.token.secret",secret),Map.entry("FRONTAPI","http://localhost:5173"),
                Map.entry("spring.jpa.properties.hibernate.session_factory.statement_inspector",Trace.class.getName()),Map.entry("logging.level.root","WARN"))));
        return new SpringApplicationBuilder(App.class).environment(env).run();
    }
    @BeforeAll void boot() throws Exception {
        // The DataSource guard verifies UUID, datadir, version and synthetic schema before Hibernate DDL.
        app=start("create");jdbc=new JdbcTemplate(app.getBean(DataSource.class));
        jdbc.execute("drop table idempotencia_adicao");
        jdbc.execute(Files.readString(Path.of("..","Dados_SQL","fase4c","proposta_idempotencia.sql")));
        app.close();app=start("validate"); // Exact approved SQL must pass the actual JPA mapping.
    }
    @AfterAll void stop(){if(second!=null)second.close();if(app!=null)app.close();}
    @BeforeEach void prepare() throws Exception {
        jdbc=new JdbcTemplate(app.getBean(DataSource.class));
        for(String t:List.of("idempotencia_adicao","item_pedido","pedido","usuario","produto_ingredientes","produto","mesa"))jdbc.update("delete from "+t);
        var mesas=app.getBean(MesaRepository.class);
        var m=new Mesa();m.setNumero(1);m.setStatus(StatusMesa.OCUPADA);m=mesas.save(m);mesaId=m.getId();
        var m2=new Mesa();m2.setNumero(2);m2.setStatus(StatusMesa.OCUPADA);m2=mesas.save(m2);
        var users=app.getBean(UsuarioRepository.class);var encoder=app.getBean(PasswordEncoder.class);
        for(var pair:List.of(new Object[]{"mesa-a",RoleName.MESA,m},new Object[]{"mesa-b",RoleName.MESA,m2},new Object[]{"gerente-teste",RoleName.GERENTE,null})) {
            var u=new Usuario();u.setUsername((String)pair[0]);u.setSenha(encoder.encode("senha-sintetica"));u.setRole((RoleName)pair[1]);u.setMesa((Mesa)pair[2]);users.save(u);
        }
        var p=new Produto();p.setNome("Pizza teste");p.setCategoria(Categoria.PIZZAS);p.setPrecoBase(new java.math.BigDecimal("10.25"));p.setQtdEstoque(20);p.setAtivo(true);p.setDescricao(new DetalhesDescricao());p=app.getBean(ProdutoRepository.class).save(p);produto=p.getId();
        pedido=novoPedido(m);
        mesaToken=login("mesa-a");outroToken=login("mesa-b");gerenteToken=login("gerente-teste");SQL.clear();
    }
    @AfterEach void integrity(){
        assertEquals(0,jdbc.queryForObject("select count(*) from produto where qtd_estoque<0",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from pedido p where p.faturamento<>(select coalesce(sum(i.quantidade*i.preco_unitario),0) from item_pedido i where i.pedido_id=p.id)",Integer.class));
    }
    private long novoPedido(Mesa m){var p=new Pedido();p.setMesa(m);p.setStatus(StatusPedido.ABERTO);p.setDataHora(java.time.LocalDateTime.now());p.setFaturamento(java.math.BigDecimal.ZERO);p.setItens(new ArrayList<>());return app.getBean(PedidoRepository.class).save(p).getId();}
    private URI uri(ConfigurableApplicationContext ctx,String path){return URI.create("http://127.0.0.1:"+((WebServerApplicationContext)ctx).getWebServer().getPort()+path);}
    private HttpResponse<String> request(ConfigurableApplicationContext ctx,String method,String path,String token,String key,String body) throws Exception {
        var b=HttpRequest.newBuilder(uri(ctx,path)).timeout(Duration.ofSeconds(18)).method(method,HttpRequest.BodyPublishers.ofString(body)).header("Content-Type","application/json");
        if(token!=null)b.header("Authorization","Bearer "+token);if(key!=null)b.header("Idempotency-Key",key);
        return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    private String login(String username) throws Exception {
        var r=request(app,"POST","/auth/login",null,null,"{\"username\":\""+username+"\",\"senha\":\"senha-sintetica\"}");assertEquals(200,r.statusCode());
        return app.getBean(tools.jackson.databind.json.JsonMapper.class).readTree(r.body()).get("token").asText();
    }
    private String body(int quantity){return "[{\"produtoId\":"+produto+",\"quantidade\":"+quantity+"}]";}
    private HttpResponse<String> add(String key,int quantity) throws Exception {return add(app,mesaToken,pedido,key,body(quantity));}
    private HttpResponse<String> add(ConfigurableApplicationContext ctx,String token,long id,String key,String body) throws Exception {return request(ctx,"PUT","/pedidos/"+id+"/itens",token,key,body);}
    private String key(){return UUID.randomUUID().toString();}
    private int records(){return jdbc.queryForObject("select count(*) from idempotencia_adicao",Integer.class);}
    private int stock(){return jdbc.queryForObject("select qtd_estoque from produto where id=?",Integer.class,produto);}
    // BINARY values need content comparison rather than byte[] identity in Maps.
    private String fingerprint(){return jdbc.queryForList("select sha2(cast(json_array(id,usuario_id,pedido_id,hex(chave),hex(payload_hash),resposta_json,status_http,criado_em,expira_em) as char),256) h from idempotencia_adicao order by id").toString()+jdbc.queryForList("select id,faturamento,status,forma_pagamento from pedido order by id")+jdbc.queryForList("select * from item_pedido order by id")+jdbc.queryForList("select id,qtd_estoque,ativo,preco_base from produto order by id")+jdbc.queryForList("select * from mesa order by id");}
    @Test void primeiraEReplayExatos() throws Exception {
        String k=key();var r=add(k,2);assertEquals(200,r.statusCode());assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        assertTrue(r.body().startsWith("{"));assertTrue(r.body().contains("20.50"));assertEquals(r.body(),jdbc.queryForObject("select resposta_json from idempotencia_adicao",String.class));
        assertEquals(18,stock());assertEquals(1,records());var before=fingerprint();SQL.clear();
        assertEquals(r.body(),add(k.toUpperCase(Locale.ROOT),2).body());assertEquals(before,fingerprint());
        assertTrue(SQL.stream().noneMatch(s->s.toLowerCase().matches("^(insert|update|delete).*")));
        assertEquals(k.replace("-", "").toUpperCase(Locale.ROOT),jdbc.queryForObject("select hex(chave) from idempotencia_adicao",String.class));
        assertEquals(16,jdbc.queryForObject("select octet_length(chave) from idempotencia_adicao",Integer.class));
        assertEquals(32,jdbc.queryForObject("select octet_length(payload_hash) from idempotencia_adicao",Integer.class));
        assertNull(jdbc.queryForObject("select expira_em from idempotencia_adicao",String.class));
        var created=jdbc.queryForObject("select criado_em from idempotencia_adicao",java.time.LocalDateTime.class);
        assertTrue(java.time.Duration.between(created,java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)).abs().toSeconds()<10);
    }
    @Test void semChavePreservaLegado() throws Exception {assertEquals(200,add(null,1).statusCode());assertEquals(200,add(null,1).statusCode());assertEquals(18,stock());assertEquals(0,records());assertTrue(SQL.stream().noneMatch(q->q.contains("idempotencia_adicao")));}
    @ParameterizedTest @ValueSource(strings={"", "x", "1-1-1-1-1", "12345678-1234-1234-1234-123456789012,12345678-1234-1234-1234-123456789012"})
    void chaveInvalidaHttp(String k) throws Exception {var before=fingerprint();var r=add(k,1);assertEquals(400,r.statusCode());assertTrue(r.body().contains("mensagem"));assertEquals(before,fingerprint());}
    @Test void headersDuplicados() throws Exception {
        var b=HttpRequest.newBuilder(uri(app,"/pedidos/"+pedido+"/itens")).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+mesaToken).header("Content-Type","application/json").header("Idempotency-Key",key()).header("Idempotency-Key",key()).PUT(HttpRequest.BodyPublishers.ofString(body(1)));
        assertEquals(400,http.send(b.build(),HttpResponse.BodyHandlers.ofString()).statusCode());assertEquals(0,records());
    }
    @Test void hashDiferenteRejeitaSemEfeitos() throws Exception {String k=key();assertEquals(200,add(k,1).statusCode());String before=fingerprint();var r=add(k,2);assertEquals(409,r.statusCode());assertTrue(r.body().contains("mensagem"));assertEquals(before,fingerprint());}
    @Test void propriedadeJsonReordenadaReplay() throws Exception {String k=key();var r=add(k,2);String before=fingerprint();var next=add(app,mesaToken,pedido,k,"[ { \"quantidade\": 2, \"produtoId\": "+produto+" } ]");assertEquals(200,next.statusCode());assertEquals(r.body(),next.body());assertEquals(before,fingerprint());}
    @Test void mesmaChaveEscoposDistintos() throws Exception {
        String k=key();assertEquals(200,add(k,1).statusCode());assertEquals(200,add(app,gerenteToken,pedido,k,body(1)).statusCode());
        long other=novoPedido(app.getBean(MesaRepository.class).findByNumero(2).orElseThrow());assertEquals(200,add(app,outroToken,other,k,body(1)).statusCode());assertEquals(3,records());assertEquals(17,stock());
    }
    @ParameterizedTest @ValueSource(strings={"AGUARDANDO_PAGAMENTO","FINALIZADO","CANCELADO"})
    void replayDepoisDeEstadoTerminalOuFechamento(String status) throws Exception {
        String k=key();var first=add(k,1);assertEquals(200,first.statusCode());
        if(!status.equals("CANCELADO")) {
            assertEquals(200,request(app,"PATCH","/pedidos/"+pedido+"/finalizar?formaPagamento=PIX",mesaToken,null,"").statusCode());
            if(status.equals("FINALIZADO"))assertEquals(200,request(app,"PATCH","/pedidos/"+pedido+"/confirmar",gerenteToken,null,"").statusCode());
        } else jdbc.update("update pedido set status='CANCELADO' where id=?",pedido);
        String before=fingerprint();var replay=add(k,1);assertEquals(200,replay.statusCode());assertEquals(first.body(),replay.body());assertEquals(before,fingerprint());assertEquals(409,add(key(),1).statusCode());assertEquals(before,fingerprint());
    }
    @Test void replayNaoRevalidaProdutoOuEstoque() throws Exception {String k=key();var r=add(k,1);jdbc.update("update produto set ativo=false,qtd_estoque=0 where id=?",produto);String before=fingerprint();assertEquals(r.body(),add(k,1).body());assertEquals(before,fingerprint());}
    @Test void autorizacaoAtualObrigatoriaMesmoComChave() throws Exception {
        String k=key();assertEquals(200,add(k,1).statusCode());String before=fingerprint();
        assertEquals(403,add(app,outroToken,pedido,k,body(1)).statusCode());
        assertEquals(403,add(app,null,pedido,k,body(1)).statusCode()); // Preserva o entrypoint existente para token ausente.
        assertEquals(401,add(app,"token-invalido",pedido,k,body(1)).statusCode());
        assertEquals(before,fingerprint());
        jdbc.update("update usuario set mesa_id=null,role='GERENTE' where username='mesa-b'");jdbc.update("update usuario set mesa_id=(select id from mesa where numero=2) where username='mesa-a'");
        assertEquals(403,add(k,1).statusCode()); // Filter reloads the current persisted association.
    }
    @Test void corsPreflight() throws Exception {
        var r=http.send(HttpRequest.newBuilder(uri(app,"/pedidos/"+pedido+"/itens")).header("Origin","http://localhost:5173").header("Access-Control-Request-Method","PUT").header("Access-Control-Request-Headers","authorization,content-type,idempotency-key").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,r.statusCode());assertEquals("http://localhost:5173",r.headers().firstValue("Access-Control-Allow-Origin").orElse(""));assertTrue(r.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase().contains("idempotency-key"));
        var denied=http.send(HttpRequest.newBuilder(uri(app,"/pedidos/"+pedido+"/itens")).header("Origin","https://nao-autorizada.invalid").header("Access-Control-Request-Method","PUT").header("Access-Control-Request-Headers","idempotency-key").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,denied.statusCode());
    }
    @Test void rollbackAntesDoCommitPelaFalhaNoRegistro() throws Exception {
        String before=fingerprint();jdbc.execute("create trigger falha_idempotencia_4d before insert on idempotencia_adicao for each row signal sqlstate '45000' set message_text='Falha sintetica antes do commit'");
        try{assertEquals(500,add(key(),1).statusCode());assertEquals(before,fingerprint());assertEquals(0,records());}
        finally{jdbc.execute("drop trigger falha_idempotencia_4d");}
    }
    @Test void respostaPerdidaDepoisDoCommit() throws Exception {String k=key();add(k,1);String stored=jdbc.queryForObject("select resposta_json from idempotencia_adicao",String.class);String before=fingerprint();var retry=add(k,1);assertEquals(200,retry.statusCode());assertEquals(stored,retry.body());assertEquals(before,fingerprint());}
    @Test void reinicioPreservaReplay() throws Exception {String k=key();var first=add(k,1);String before=fingerprint();app.close();app=start("validate");jdbc=new JdbcTemplate(app.getBean(DataSource.class));assertEquals(first.body(),add(k,1).body());assertEquals(before,fingerprint());}
    @Test void duasInstanciasMesmoUuid() throws Exception {
        second=start("validate");try {String k=key();var r=concurrent(List.of(()->add(k,1),()->add(second,mesaToken,pedido,k,body(1))));assertEquals(200,r.get(0).statusCode());assertEquals(200,r.get(1).statusCode());assertEquals(r.get(0).body(),r.get(1).body());assertEquals(1,records());assertEquals(19,stock());}finally{second.close();second=null;}
    }
    @Test void concorrenciaMesmaChave() throws Exception {String k=key();var r=concurrent(List.of(()->add(k,1),()->add(k,1)));assertEquals(List.of(200,200),r.stream().map(HttpResponse::statusCode).toList());assertEquals(r.get(0).body(),r.get(1).body());assertEquals(1,records());assertEquals(19,stock());}
    @Test void concorrenciaMesmoUuidConteudoDiferente() throws Exception {String k=key();var r=concurrent(List.of(()->add(k,1),()->add(k,2)));assertEquals(List.of(200,409),r.stream().map(HttpResponse::statusCode).sorted().toList());assertEquals(1,records());assertTrue(stock()==19 || stock()==18);}
    @Test void concorrenciaChavesDistintas() throws Exception {var r=concurrent(List.of(()->add(key(),1),()->add(key(),2)));assertEquals(List.of(200,200),r.stream().map(HttpResponse::statusCode).toList());assertEquals(2,records());assertEquals(17,stock());}
    @Test void concorrenciaUltimaUnidade() throws Exception {jdbc.update("update produto set qtd_estoque=1 where id=?",produto);var r=concurrent(List.of(()->add(key(),1),()->add(key(),1)));assertEquals(List.of(200,409),r.stream().map(HttpResponse::statusCode).sorted().toList());assertEquals(0,stock());assertEquals(1,records());}
    @ParameterizedTest @ValueSource(strings={"[]","[null]","[{\"produtoId\":1,\"quantidade\":0}]","[{\"produtoId\":0,\"quantidade\":1}]"})
    void estruturaInvalidaComChave(String payload) throws Exception {
        String before=fingerprint();var r=add(app,mesaToken,pedido,key(),payload);assertEquals(400,r.statusCode());assertTrue(r.body().contains("mensagem"));assertEquals(before,fingerprint());assertEquals(0,records());
    }
    @Test void precisaoMonetariaGrandeESnapshotImutavel() throws Exception {
        jdbc.update("update produto set preco_base=12345678901234567890.12,nome=? where id=?","Muçarela 🍕",produto);
        String k=key();var first=add(k,1);assertEquals(200,first.statusCode());assertTrue(first.body().contains("12345678901234567890.12"));assertTrue(first.body().contains("Muçarela 🍕"));
        jdbc.update("update produto set preco_base=0.01,nome='Nome alterado' where id=?",produto);
        String before=fingerprint();var replay=add(k,1);assertEquals(200,replay.statusCode());assertEquals(first.body(),replay.body());assertEquals(before,fingerprint());
    }
    @Test void timeoutComChaveReverteEPermiteNovaTentativa() throws Exception {
        String k=key();String before=fingerprint();var pool=Executors.newSingleThreadExecutor();
        try(var holder=app.getBean(DataSource.class).getConnection()) {
            holder.setAutoCommit(false);holder.createStatement().executeQuery("select id from mesa where id="+mesaId+" for update").close();
            var future=pool.submit(()->add(k,1));var response=future.get(15,TimeUnit.SECONDS);assertEquals(409,response.statusCode());
            assertTrue(response.body().contains("mensagem"));holder.rollback();assertEquals(before,fingerprint());assertEquals(0,records());
        }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
        assertEquals(200,add(k,1).statusCode());assertEquals(19,stock());assertEquals(1,records());
    }
    private List<HttpResponse<String>> concurrent(List<Callable<HttpResponse<String>>> work) throws Exception {
        var pool=Executors.newFixedThreadPool(work.size());var start=new CountDownLatch(1);
        try(var holder=app.getBean(DataSource.class).getConnection()) {
            holder.setAutoCommit(false);holder.createStatement().executeQuery("select id from mesa where id="+mesaId+" for update").close();SQL.clear();
            var fs=new ArrayList<Future<HttpResponse<String>>>();for(var task:work)fs.add(pool.submit(()->{assertTrue(start.await(6,TimeUnit.SECONDS));return task.call();}));start.countDown();
            long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);int waits=0;
            while(System.nanoTime()<limit){waits=jdbc.queryForObject("select count(distinct w.requesting_thread_id) from performance_schema.data_lock_waits w join performance_schema.threads t on t.thread_id=w.requesting_thread_id where t.processlist_db=database()",Integer.class);if(waits>=work.size())break;LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));}
            assertEquals(work.size(),waits,"HTTP requests must overlap on actual MySQL locks");holder.commit();
            var result=new ArrayList<HttpResponse<String>>();for(var f:fs)result.add(f.get(20,TimeUnit.SECONDS));
            var locks=SQL.stream().map(String::toLowerCase).filter(s->s.contains("for update")).toList();assertFalse(locks.isEmpty());assertTrue(locks.get(0).contains("mesa"));
            assertTrue(locks.stream().anyMatch(s->s.contains("pedido")));assertTrue(locks.stream().anyMatch(s->s.contains("produto")));
            return result;
        } finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
}

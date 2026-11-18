package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.controller.DashboardController;
import inf.anibele.pizzariamauabackend.model.Categoria;
import inf.anibele.pizzariamauabackend.model.StatusPedido;
import inf.anibele.pizzariamauabackend.repository.ItemPedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "TEST_MYSQL_URL", matches = "jdbc:mysql://127[.]0[.]0[.]1:.*")
@SpringJUnitConfig(FinanceiroMysqlTest.Config.class)
class RankingPizzasMysqlTest {
    @Autowired DataSource ds;
    @Autowired DashboardService dashboard;
    @Autowired ItemPedidoRepository itens;
    JdbcTemplate jdbc;
    long pedidoId;
    static final LocalDateTime DIA = LocalDateTime.of(2026, 11, 17, 12, 0);

    @BeforeEach void preparar() {
        jdbc = new JdbcTemplate(ds);
        for (String table : List.of("idempotencia_adicao", "item_pedido", "pedido", "usuario", "produto_ingredientes", "produto", "mesa")) {
            jdbc.update("delete from " + table);
        }
        pedidoId = 0;
    }

    void produto(long id, String nome, Categoria categoria) {
        jdbc.update("insert into produto(id,nome,categoria,preco_base,qtd_estoque,ativo,prato_vegano) values(?,?,?,10.25,1000,true,false)", id, nome, categoria.name());
    }

    void venda(long produto, int quantidade, StatusPedido status, LocalDateTime abertura) {
        long id = ++pedidoId;
        jdbc.update("insert into mesa(id,numero,ativo,status) values(?,?,true,'OCUPADA')", id, id);
        jdbc.update("insert into pedido(id,mesa_id,data_hora,faturamento,status) values(?,?,?, ?,?)", id, id, abertura, new java.math.BigDecimal("10.25").multiply(java.math.BigDecimal.valueOf(quantidade)), status.name());
        jdbc.update("insert into item_pedido(id,pedido_id,produto_id,quantidade,preco_unitario,status,data_hora_inclusao) values(?,?,?,?,10.25,'PENDENTE',?)", id, id, produto, quantidade, abertura);
    }

    @ParameterizedTest @EnumSource(Categoria.class)
    void classificaPeloEnumNaoPeloNome(Categoria categoria) {
        produto(1, "Pizza no nome nao determina categoria", categoria);
        venda(1, 7, StatusPedido.FINALIZADO, DIA);
        var resultado = dashboard.obterRankings().topPizzas();
        assertEquals(categoria == Categoria.PIZZAS ? 1 : 0, resultado.size());
        if (!resultado.isEmpty()) assertEquals(7L, resultado.get(0).quantidadeVendida());
    }

    @Test void pizzaDoceClassificadaComoPizzaEntraSobremesaNao() {
        produto(1, "Pizza Chocolate", Categoria.PIZZAS);
        produto(2, "Chocolate Sobremesa", Categoria.SOBREMESAS);
        venda(1, 2, StatusPedido.FINALIZADO, DIA);
        venda(2, 100, StatusPedido.FINALIZADO, DIA);
        assertEquals(List.of("Pizza Chocolate"), dashboard.obterRankings().topPizzas().stream().map(p -> p.nome()).toList());
    }

    @ParameterizedTest @EnumSource(StatusPedido.class)
    void statusMantemDefinicaoOperacional(StatusPedido status) {
        produto(1, "Pizza salgada", Categoria.PIZZAS);
        venda(1, 3, status, DIA);
        assertEquals(status == StatusPedido.CANCELADO ? 0 : 1, dashboard.obterRankings().topPizzas().size());
    }

    @Test void quantidadesDesempateLimiteIngredientesEContrato() throws Exception {
        for (long id = 1; id <= 7; id++) produto(id, "Pizza " + id, Categoria.PIZZAS);
        venda(1, 2, StatusPedido.ABERTO, DIA);
        venda(1, 3, StatusPedido.FINALIZADO, DIA);
        venda(2, 9, StatusPedido.AGUARDANDO_PAGAMENTO, DIA);
        venda(3, 5, StatusPedido.FINALIZADO, DIA);
        venda(4, 2, StatusPedido.FINALIZADO, DIA);
        venda(5, 1, StatusPedido.FINALIZADO, DIA);
        venda(6, 1, StatusPedido.FINALIZADO, DIA);
        jdbc.update("insert into produto_ingredientes(produto_id,ingrediente) values(1,'A'),(1,'B'),(1,'C')");
        var antes = snapshot();
        var ranking = dashboard.obterRankings().topPizzas();
        assertEquals(List.of("Pizza 2", "Pizza 1", "Pizza 3", "Pizza 4", "Pizza 5"), ranking.stream().map(p -> p.nome()).toList());
        assertEquals(List.of(9L, 5L, 5L, 2L, 1L), ranking.stream().map(p -> p.quantidadeVendida()).toList());
        var mvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboard)).build();
        mvc.perform(get("/api/dashboard/rankings")).andExpect(status().isOk())
                .andExpect(jsonPath("$.topPizzas.length()").value(5))
                .andExpect(jsonPath("$.topPizzas[0].nome").value("Pizza 2"))
                .andExpect(jsonPath("$.topPizzas[0].quantidadeVendida").value(9));
        assertEquals(antes, snapshot());
    }

    @Test void produtosDistintosComMesmoNomeNaoSaoFundidos() {
        produto(1, "Mesmo nome", Categoria.PIZZAS);
        produto(2, "Mesmo nome", Categoria.PIZZAS);
        venda(1, 3, StatusPedido.FINALIZADO, DIA);
        venda(2, 2, StatusPedido.FINALIZADO, DIA);
        assertEquals(List.of(3L, 2L), dashboard.obterRankings().topPizzas().stream().map(p -> p.quantidadeVendida()).toList());
    }

    @Test void semVendaEListaVazia() {
        assertTrue(dashboard.obterRankings().topPizzas().isEmpty());
        produto(1, "Sem venda", Categoria.PIZZAS);
        assertTrue(dashboard.obterRankings().topPizzas().isEmpty());
    }

    @Test void periodoMensalInclusivoExclusivoSemDiaSeguinte() {
        produto(1, "Pizza", Categoria.PIZZAS);
        venda(1, 100, StatusPedido.FINALIZADO, LocalDateTime.of(2026, 10, 31, 23, 59, 59, 999999000));
        venda(1, 1, StatusPedido.FINALIZADO, LocalDateTime.of(2026, 11, 1, 0, 0));
        venda(1, 2, StatusPedido.FINALIZADO, LocalDateTime.of(2026, 11, 17, 23, 59, 59, 999999000));
        venda(1, 100, StatusPedido.FINALIZADO, LocalDateTime.of(2026, 11, 18, 0, 0));
        assertEquals(3L, dashboard.obterRankings().topPizzas().get(0).quantidadeVendida());
        assertEquals(3L, itens.buscarTopProdutos(LocalDateTime.of(2026,11,1,0,0), LocalDateTime.of(2026,11,18,0,0)).get(0).quantidadeVendida());
    }

    List<List<Map<String,Object>>> snapshot() {
        var rows = new ArrayList<List<Map<String,Object>>>();
        for (String table : List.of("pedido", "mesa", "produto", "item_pedido", "produto_ingredientes")) {
            rows.add(jdbc.queryForList("select * from " + table + " order by " + (table.equals("produto_ingredientes") ? "produto_id,ingrediente" : "id")));
        }
        return rows;
    }
}

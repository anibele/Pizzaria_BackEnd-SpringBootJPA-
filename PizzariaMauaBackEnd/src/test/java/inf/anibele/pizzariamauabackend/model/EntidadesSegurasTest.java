package inf.anibele.pizzariamauabackend.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EntidadesSegurasTest {
    Object novo(boolean item) { return item ? new ItemPedido() : new Pedido(); }
    void id(Object entity, Long id) {
        if (entity instanceof Pedido p) p.setId(id);
        else ((ItemPedido) entity).setId(id);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void transientesDistintosNaoSaoIguais(boolean item) {
        var a=novo(item);var b=novo(item);
        assertEquals(a,a);assertNotEquals(a,b);assertNotEquals(a,null);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void idDefineIdentidadeSemCompararGrafo(boolean item) {
        var a=novo(item);var b=novo(item);id(a,1L);id(b,1L);
        assertEquals(a,b);assertEquals(b,a);assertEquals(a.hashCode(),b.hashCode());
        id(b,2L);assertNotEquals(a,b);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void hashPermaneceEstavelAoPersistir(boolean item) {
        var a=novo(item);var set=new HashSet<>();set.add(a);int hash=a.hashCode();
        id(a,1L);assertEquals(hash,a.hashCode());assertTrue(set.contains(a));
    }
    @Test void cicloPedidoItemNaoRecursaEmLogOuComparacao() {
        var p=new Pedido();p.setId(10L);var item=new ItemPedido();item.setId(20L);
        p.setItens(List.of(item));item.setPedido(p);
        assertEquals("Pedido(id=10)",p.toString());assertEquals("ItemPedido(id=20)",item.toString());
        assertNotEquals(p,item);
    }
    @Test void credencialNaoApareceEmLogOuSerializacao() {
        var u=new Usuario();u.setId(1L);u.setUsername("sintetico");u.setRole(RoleName.GERENTE);
        u.setSenha("credencial-sintetica-nao-publicar");
        assertFalse(u.toString().contains(u.getSenha()));
        String json=JsonMapper.builder().findAndAddModules().build().writeValueAsString(u);
        assertFalse(json.contains(u.getSenha()));assertFalse(json.contains("\"senha\""));assertFalse(json.contains("\"password\""));
    }
}

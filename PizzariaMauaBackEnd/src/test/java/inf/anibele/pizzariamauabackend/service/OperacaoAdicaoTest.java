package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.ItemPedidoRequestDTO;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OperacaoAdicaoTest {
    @ParameterizedTest @ValueSource(strings={"", "x", "1-1-1-1-1", " 12345678-1234-1234-1234-123456789012", "12345678-1234-1234-1234-123456789012,x"})
    void chaveInvalida(String valor) {
        assertEquals(400,assertThrows(ResponseStatusException.class,()->OperacaoAdicao.validarChave(List.of(valor))).getStatusCode().value());
    }
    @Test void ausenciaERepeticoesDeHeader() {
        assertNull(OperacaoAdicao.validarChave(null));
        assertThrows(ResponseStatusException.class,()->OperacaoAdicao.validarChave(List.of()));
        assertThrows(ResponseStatusException.class,()->OperacaoAdicao.validarChave(List.of(UUID.randomUUID().toString(),UUID.randomUUID().toString())));
    }
    @Test void uuidBinarioMesmoValorMaiusculo() {
        var key=UUID.randomUUID();assertEquals(key,OperacaoAdicao.validarChave(List.of(key.toString().toUpperCase(Locale.ROOT))));
    }
    @Test void vetorV1Estavel() {
        var itens=List.of(new ItemPedidoRequestDTO(3L,2));byte[] hash=OperacaoAdicao.hash(15L,itens);
        assertEquals(32,hash.length);
        assertEquals("97bff4bddf9cd9dbf001611c74af4ba0af5ea1debbdc6d364fc78630493f47d9",HexFormat.of().formatHex(hash));
        assertArrayEquals(hash,OperacaoAdicao.hash(15L,itens));
    }
    @Test void jsonOrdenacaoEWhitespaceNaoImportam() {
        var mapper=JsonMapper.builder().build();
        var a=mapper.readValue("[{\"produtoId\":3,\"quantidade\":2}]",ItemPedidoRequestDTO[].class);
        var b=mapper.readValue("[ { \"quantidade\": 2, \"produtoId\": 3 } ]",ItemPedidoRequestDTO[].class);
        assertArrayEquals(OperacaoAdicao.hash(15L,List.of(a)),OperacaoAdicao.hash(15L,List.of(b)));
    }
    @Test void camposOrdemRepeticoesEPedidoImportam() {
        var original=OperacaoAdicao.hash(15L,List.of(new ItemPedidoRequestDTO(3L,2)));
        for(var itens:List.of(List.of(new ItemPedidoRequestDTO(4L,2)),List.of(new ItemPedidoRequestDTO(3L,1)),List.of(new ItemPedidoRequestDTO(3L,1),new ItemPedidoRequestDTO(3L,1))))
            assertFalse(Arrays.equals(original,OperacaoAdicao.hash(15L,itens)));
        assertFalse(Arrays.equals(original,OperacaoAdicao.hash(16L,List.of(new ItemPedidoRequestDTO(3L,2)))));
        var a=new ItemPedidoRequestDTO(1L,1);var b=new ItemPedidoRequestDTO(2L,1);
        assertFalse(Arrays.equals(OperacaoAdicao.hash(15L,List.of(a,b)),OperacaoAdicao.hash(15L,List.of(b,a))));
    }
}

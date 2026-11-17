package inf.anibele.pizzariamauabackend.mapper;

import inf.anibele.pizzariamauabackend.controller.ProdutoController;
import inf.anibele.pizzariamauabackend.exception.ApiExceptionHandler;
import inf.anibele.pizzariamauabackend.model.DetalhesDescricao;
import inf.anibele.pizzariamauabackend.model.ItemPedido;
import inf.anibele.pizzariamauabackend.model.Produto;
import inf.anibele.pizzariamauabackend.repository.ProdutoRepository;
import inf.anibele.pizzariamauabackend.service.ProdutoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TempoPreparoTest {

    private ProdutoRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        repository = mock(ProdutoRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProdutoController(new ProdutoService(repository, new ProdutoMapper())))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    static Stream<Arguments> tempos() {
        return Stream.of(Arguments.of("20 min", 20, true), Arguments.of("0 min", 0, true),
                Arguments.of(null, 0, true), Arguments.of("", 0, true), Arguments.of("   ", 0, true),
                Arguments.of("20-30 min", 0, false), Arguments.of("1h30", 0, false),
                Arguments.of("texto inválido", 0, false), Arguments.of("20", 0, false),
                Arguments.of("20 minutos", 0, false), Arguments.of("-20 min", 0, false),
                Arguments.of("1.5 min", 0, false), Arguments.of("2147483647 min", Integer.MAX_VALUE, true),
                Arguments.of("2147483648 min", 0, false), Arguments.of("999999999999999999999999 min", 0, false));
    }

    @ParameterizedTest
    @MethodSource("tempos")
    void leituraDeDadosAntigosNaoFalhaNemAdivinhaFormato(String texto, int esperado, boolean valido) {
        DetalhesDescricao descricao = new DetalhesDescricao();
        descricao.setTempoMedioPreparo(texto);
        Produto produto = new Produto();
        produto.setId(1L);
        produto.setDescricao(descricao);
        ItemPedido item = new ItemPedido();
        item.setProduto(produto);

        var dto = assertDoesNotThrow(() -> new ItemPedidoMapper().toResponseDTO(item));

        assertEquals(esperado, dto.tempoPreparoMinutos());
        assertEquals(texto, produto.getDescricao().getTempoMedioPreparo());
    }

    static Stream<Arguments> entradas() {
        return tempos().flatMap(args -> Stream.of(
                Arguments.of(args.get()[0], args.get()[2], HttpMethod.POST, "/produtos"),
                Arguments.of(args.get()[0], args.get()[2], HttpMethod.PUT, "/produtos/1")));
    }

    @ParameterizedTest
    @MethodSource("entradas")
    void validaFormatoNoCadastroENaEdicao(String texto, boolean valido, HttpMethod metodo, String url) throws Exception {
        if (valido) {
            when(repository.save(any(Produto.class))).thenAnswer(invocation -> invocation.getArgument(0));
            if (metodo == HttpMethod.PUT) when(repository.findById(1L)).thenReturn(Optional.of(new Produto()));
        }
        String valor = texto == null ? "null" : "\"" + texto + "\"";
        String json = "{\"nome\":\"Pizza\",\"categoria\":\"PIZZAS\",\"precoBase\":50,\"qtdEstoque\":10,\"ativo\":true,"
                + "\"descricao\":{\"pratoVegano\":false,\"tempoMedioPreparo\":" + valor + "}}";
        mvc.perform(request(metodo, url).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(valido ? (metodo == HttpMethod.POST ? 201 : 200) : 400));
        if (!valido) verifyNoInteractions(repository);
    }
}

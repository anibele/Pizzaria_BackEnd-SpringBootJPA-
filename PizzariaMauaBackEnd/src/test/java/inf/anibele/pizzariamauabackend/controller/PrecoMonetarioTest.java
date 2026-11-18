package inf.anibele.pizzariamauabackend.controller;

import inf.anibele.pizzariamauabackend.exception.ApiExceptionHandler;
import inf.anibele.pizzariamauabackend.service.ProdutoService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PrecoMonetarioTest {
    @ParameterizedTest @ValueSource(strings={"0.001","1.005","9999999999999999999999999999999999999"})
    void precoIncompativelComDecimalDoBancoRetorna400AntesDeGravar(String preco) throws Exception {
        var service=mock(ProdutoService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new ProdutoController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        String body="{\"nome\":\"Sintetico\",\"categoria\":\"PIZZAS\",\"precoBase\":"+preco+",\"ativo\":true,\"qtdEstoque\":10}";
        mvc.perform(post("/produtos").contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").exists());
        verifyNoInteractions(service);
    }
}

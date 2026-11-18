package inf.anibele.pizzariamauabackend.controller;

import inf.anibele.pizzariamauabackend.exception.*;
import inf.anibele.pizzariamauabackend.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ItensStatusHttpTest {
    private PedidoService service;
    private MockMvc mvc;
    @BeforeEach void preparar() {
        service=mock(PedidoService.class);
        mvc=MockMvcBuilders.standaloneSetup(new PedidoController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
    }
    @Test void statusInvalidoRetorna400AntesDoService() throws Exception {
        mvc.perform(patch("/pedidos/itens/1/status?status=INVALIDO")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").isNotEmpty());
        verifyNoInteractions(service);
    }
    @Test void statusAusenteRetorna400() throws Exception {
        mvc.perform(patch("/pedidos/itens/1/status")).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void itemInexistenteRetorna404() throws Exception {
        doThrow(new RecursoNaoEncontradoException("Item nao encontrado.")).when(service).atualizarStatusItem(1L,inf.anibele.pizzariamauabackend.model.StatusItemPedido.PRONTO);
        mvc.perform(patch("/pedidos/itens/1/status?status=PRONTO")).andExpect(status().isNotFound()).andExpect(jsonPath("$.mensagem").isNotEmpty());
    }
    @Test void conflitoRetorna409ComMensagem() throws Exception {
        doThrow(new ConflitoException("Transicao nao permitida.")).when(service).atualizarStatusItem(1L,inf.anibele.pizzariamauabackend.model.StatusItemPedido.PENDENTE);
        mvc.perform(patch("/pedidos/itens/1/status?status=PENDENTE")).andExpect(status().isConflict()).andExpect(jsonPath("$.mensagem").value("Transicao nao permitida."));
    }
    @Test void sucessoMantem204SemCorpo() throws Exception {
        mvc.perform(patch("/pedidos/itens/1/status?status=PRONTO")).andExpect(status().isNoContent()).andExpect(content().string(""));
    }
}

package inf.anibele.pizzariamauabackend.controller;

import inf.anibele.pizzariamauabackend.exception.ApiExceptionHandler;
import inf.anibele.pizzariamauabackend.service.PedidoService;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.stream.Stream;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConflitoLockHttpTest {
    static Stream<RuntimeException> falhas() {
        return Stream.of(new CannotAcquireLockException("SQL interno"),new LockTimeoutException("SQL interno"),new PessimisticLockException("SQL interno"));
    }
    @ParameterizedTest @MethodSource("falhas")
    void conflitoDeLockRetorna409SemDetalhesInternos(RuntimeException falha) throws Exception {
        var service=mock(PedidoService.class);when(service.confirmarPagamentoEFecharMesa(1L)).thenThrow(falha);
        var mvc=MockMvcBuilders.standaloneSetup(new PedidoController(service)).setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(patch("/pedidos/1/confirmar")).andExpect(status().isConflict())
                .andExpect(content().json("{\"mensagem\":\"Operacao concorrente em andamento. Atualize os dados e tente novamente.\"}"));
        verify(service).confirmarPagamentoEFecharMesa(1L);
    }
}

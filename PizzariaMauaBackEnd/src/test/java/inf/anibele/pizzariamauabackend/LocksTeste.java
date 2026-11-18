package inf.anibele.pizzariamauabackend;

import jakarta.persistence.EntityManager;
import inf.anibele.pizzariamauabackend.repository.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

// Somente mocks: os testes JPA/MySQL usam os locks reais, nunca estes aliases.
public final class LocksTeste {
    public static void configurar(Object service, Object... repositories) {
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));
        for (Object repository : repositories) {
            if (repository instanceof MesaRepository m) lenient().when(m.findByNumeroForUpdate(any())).thenAnswer(i->m.findByNumero(i.getArgument(0)));
            if (repository instanceof PedidoRepository p) {
                lenient().when(p.findByIdForUpdate(any())).thenAnswer(i->p.findById(i.getArgument(0)));
            }
            if (repository instanceof ProdutoRepository p) lenient().when(p.findByIdForUpdate(any())).thenAnswer(i->{
                var found=p.findById(i.getArgument(0));
                return found.isPresent()?found:p.findByIdAndAtivoTrue(i.getArgument(0));
            });
            clearInvocations(repository);
        }
    }
}

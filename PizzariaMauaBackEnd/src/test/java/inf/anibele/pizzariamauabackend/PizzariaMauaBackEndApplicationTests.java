package inf.anibele.pizzariamauabackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import inf.anibele.pizzariamauabackend.repository.*;
import java.security.SecureRandom;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Todas as propriedades de conexao sobrescritas: nunca utiliza MySQL nem seeds.
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:contextfase3b;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
    "spring.profiles.active=test"
})
class PizzariaMauaBackEndApplicationTests {
    @Autowired UsuarioRepository usuarios;
    @Autowired MesaRepository mesas;
    @Autowired ProdutoRepository produtos;
    @DynamicPropertySource static void segredoDeTeste(DynamicPropertyRegistry properties) {
        byte[] key=new byte[32];new SecureRandom().nextBytes(key);
        String value=Base64.getEncoder().encodeToString(key);
        properties.add("api.security.token.secret",()->value);
    }
    @Test void contextLoads() {
        assertEquals(0,usuarios.count());assertEquals(0,mesas.count());assertEquals(0,produtos.count());
    }
}

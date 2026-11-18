package inf.anibele.pizzariamauabackend.config;

import inf.anibele.pizzariamauabackend.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfiguracaoFase3BTest {
    @Test void inicializacaoSemSegredoFalhaSemCriarDatasource() {
        try (var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("teste",Map.of("api.security.token.secret","")));
            context.register(TokenService.class);
            var error=assertThrows(BeansException.class,context::refresh);
            assertTrue(error.getMostSpecificCause().getMessage().contains("API_SECURITY_TOKEN_SECRET"));
        }
    }
    @Test void configuracaoPadraoValidaSchemaSemAtivarDemo() throws Exception {
        var properties=ler("application.properties");
        assertEquals("validate",properties.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("${DB_USERNAME}",properties.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}",properties.getProperty("spring.datasource.password"));
        assertNull(properties.getProperty("spring.profiles.active"));
        assertNull(properties.getProperty("spring.profiles.default"));
        assertEquals("${API_SECURITY_TOKEN_SECRET:}",properties.getProperty("api.security.token.secret"));
        assertFalse(properties.getProperty("spring.datasource.url").contains("createDatabaseIfNotExist"));
    }
    @Test void implantacaoExigeConexaoExternaEPreservaValidate() throws Exception {
        var p=ler("application-prod.properties");
        assertEquals("${DB_URL}",p.getProperty("spring.datasource.url"));
        assertEquals("${DB_USERNAME}",p.getProperty("spring.datasource.username"));
        assertEquals("${DB_PASSWORD}",p.getProperty("spring.datasource.password"));
        assertEquals("validate",p.getProperty("spring.jpa.hibernate.ddl-auto"));
    }
    private Properties ler(String file) throws Exception {
        var p=new Properties();
        try(var in=getClass().getClassLoader().getResourceAsStream(file)){assertNotNull(in);p.load(in);}
        return p;
    }
}

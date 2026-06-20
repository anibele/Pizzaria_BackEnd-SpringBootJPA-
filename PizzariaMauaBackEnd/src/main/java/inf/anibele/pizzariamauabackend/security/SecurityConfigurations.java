package inf.anibele.pizzariamauabackend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfigurations {

    @Autowired
    private SecurityFilter securityFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(req -> {
                    // Libera requisições de teste (preflight) do navegador
                    req.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    // 1. ROTA PÚBLICA
                    req.requestMatchers(HttpMethod.POST, "/auth/login").permitAll();

                    // 2. REGRAS DA COZINHA (Baseado na sua PedidoController e MesaController)
                    req.requestMatchers(HttpMethod.GET, "/pedidos/cozinha").hasAnyAuthority("COZINHA", "GERENTE");
                    req.requestMatchers(HttpMethod.PATCH, "/pedidos/itens/*/status").hasAnyAuthority("COZINHA", "GERENTE");
                    req.requestMatchers(HttpMethod.GET, "/mesas").hasAnyAuthority("COZINHA", "GERENTE"); // Cozinha monitora salão

                    // 3. REGRAS DA MESA / TABLET (Usando '*' no meio para os IDs e números das variáveis)
                    req.requestMatchers(HttpMethod.GET, "/produtos/ativos").hasAnyAuthority("MESA", "GERENTE");
                    req.requestMatchers(HttpMethod.GET, "/produtos/*").hasAnyAuthority("MESA", "GERENTE"); // Buscar por ID
                    req.requestMatchers(HttpMethod.POST, "/pedidos").hasAnyAuthority("MESA", "GERENTE"); // Abrir pedido
                    req.requestMatchers(HttpMethod.PUT, "/pedidos/*/itens").hasAnyAuthority("MESA", "GERENTE"); // Adicionar itens (Corrigido de ** para *)
                    req.requestMatchers(HttpMethod.PATCH, "/pedidos/*/finalizar").hasAnyAuthority("MESA", "GERENTE"); // Finalizar (Corrigido de ** para *)
                    req.requestMatchers(HttpMethod.GET, "/pedidos/mesa/*/aberto").hasAnyAuthority("MESA", "GERENTE"); // Sincronizar tablet
                    req.requestMatchers(HttpMethod.GET, "/mesas/*").hasAnyAuthority("MESA", "GERENTE"); // Consultar dados da própria mesa

                    // 4. REGRAS DO GERENTE (CRUDs e rotas administrativas exclusivas)
                    req.requestMatchers(HttpMethod.POST, "/produtos").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.GET, "/produtos").hasAuthority("GERENTE"); // Listar todos (ativos e inativos)
                    req.requestMatchers(HttpMethod.PUT, "/produtos/*").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.PATCH, "/produtos/*/status").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.DELETE, "/produtos/*").hasAuthority("GERENTE");

                    req.requestMatchers(HttpMethod.POST, "/mesas").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.GET, "/mesas/status").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.PATCH, "/mesas/*/status").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.DELETE, "/mesas/*").hasAuthority("GERENTE");

                    // Quaisquer outras rotas administrativas futuras (como relatórios)
                    req.requestMatchers("/relatorios/**", "/usuarios/**").hasAuthority("GERENTE");

                    // 5. BLOQUEIO PADRÃO
                    req.anyRequest().authenticated();
                })
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000")); // Suas portas do React
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")); // Incluído o PATCH que você usa bastante!
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
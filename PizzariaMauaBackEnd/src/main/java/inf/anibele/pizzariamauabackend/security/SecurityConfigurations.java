package inf.anibele.pizzariamauabackend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${FRONTAPI}")
    private String FRONTAPI;

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

                    // 2. REGRAS DA COZINHA
                    req.requestMatchers(HttpMethod.GET, "/pedidos/cozinha").hasAnyAuthority("COZINHA", "GERENTE", "CAIXA");
                    req.requestMatchers(HttpMethod.PATCH, "/pedidos/itens/*/status").hasAnyAuthority("COZINHA", "GERENTE");
                    // Adicionamos o CAIXA aqui para ele também poder ver o status geral das mesas
                    req.requestMatchers(HttpMethod.GET, "/mesas").hasAnyAuthority("COZINHA", "GERENTE", "CAIXA");

                    // 3. REGRAS DA MESA / TABLET
                    req.requestMatchers(HttpMethod.GET, "/produtos/ativos").hasAnyAuthority("MESA", "GERENTE");
                    req.requestMatchers(HttpMethod.GET, "/produtos/*").hasAnyAuthority("MESA", "GERENTE");
                    req.requestMatchers(HttpMethod.POST, "/pedidos").hasAnyAuthority("MESA", "GERENTE");
                    req.requestMatchers(HttpMethod.PUT, "/pedidos/*/itens").hasAnyAuthority("MESA", "GERENTE");
                    req.requestMatchers(HttpMethod.PATCH, "/pedidos/*/finalizar").hasAnyAuthority("MESA", "GERENTE", "CAIXA");
                    req.requestMatchers(HttpMethod.GET, "/pedidos/mesa/*/aberto").hasAnyAuthority("MESA", "GERENTE", "CAIXA");
                    req.requestMatchers(HttpMethod.GET, "/mesas/*").hasAnyAuthority("MESA", "GERENTE", "CAIXA");

                    // 4. REGRAS DO CAIXA E GERENTE (Operações de salão e pagamento)
                    req.requestMatchers(HttpMethod.PATCH, "/pedidos/*/confirmar").hasAnyAuthority("CAIXA", "GERENTE");
                    req.requestMatchers(HttpMethod.GET, "/mesas/status").hasAnyAuthority("GERENTE", "CAIXA");
                    req.requestMatchers(HttpMethod.PATCH, "/mesas/*/status").hasAnyAuthority("GERENTE", "CAIXA");
                    req.requestMatchers(HttpMethod.GET, "/pedidos/finalizados").hasAnyAuthority("CAIXA", "GERENTE");
                    req.requestMatchers(HttpMethod.GET, "/pedidos/faturamento").hasAnyAuthority("CAIXA", "GERENTE");
                    req.requestMatchers(HttpMethod.GET, "/pedidos/cozinha").hasAnyAuthority("CAIXA", "GERENTE");
                    req.requestMatchers(HttpMethod.GET, "/pedidos/**").hasAnyRole("COZINHA", "GERENTE", "CAIXA");

                    // 5. REGRAS DO GERENTE (CRUDs exclusivos)
                    req.requestMatchers(HttpMethod.POST, "/produtos").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.GET, "/produtos").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.PUT, "/produtos/*").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.PATCH, "/produtos/*/status").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.DELETE, "/produtos/*").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.GET, "/api/dashboard/**").hasAuthority("GERENTE");

                    req.requestMatchers(HttpMethod.POST, "/mesas").hasAuthority("GERENTE");
                    req.requestMatchers(HttpMethod.DELETE, "/mesas/*").hasAuthority("GERENTE");

                    req.requestMatchers("/relatorios/**", "/usuarios/**").hasAuthority("GERENTE");

                    // 6. BLOQUEIO PADRÃO
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
        configuration.setAllowedOrigins(List.of(FRONTAPI, "http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
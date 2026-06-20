package inf.anibele.pizzariamauabackend.controller;

import inf.anibele.pizzariamauabackend.dto.DadosLoginDTO;
import inf.anibele.pizzariamauabackend.model.Usuario;
import inf.anibele.pizzariamauabackend.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AutenticacaoController {

    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity efetuarLogin(@RequestBody DadosLoginDTO dados) {
        var authenticationToken = new UsernamePasswordAuthenticationToken(dados.username(), dados.senha());

        // Aqui o Spring vai lá no AutenticacaoService, busca o usuário e verifica a senha
        var authentication = manager.authenticate(authenticationToken);

        // Se a senha bater, geramos o token
        var usuarioLogado = (Usuario) authentication.getPrincipal();
        assert usuarioLogado != null;
        var tokenJWT = tokenService.gerarToken(usuarioLogado);

        // Devolvemos o token e as informações da pessoa
        return ResponseEntity.ok(new DadosRetornoLogin(tokenJWT, usuarioLogado.getUsername(), usuarioLogado.getRole().name()));
    }

    // DTO interno só para devolver a resposta estruturada
    private record DadosRetornoLogin(String token, String username, String role) {}
}
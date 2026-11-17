package inf.anibele.pizzariamauabackend.exception;

import inf.anibele.pizzariamauabackend.controller.MesaController;
import inf.anibele.pizzariamauabackend.controller.PedidoController;
import inf.anibele.pizzariamauabackend.controller.ProdutoController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {ProdutoController.class, MesaController.class, PedidoController.class})
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Object> tratarNaoEncontrado(RecursoNaoEncontradoException exception) {
        return resposta(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<Object> tratarConflito(ConflitoException exception) {
        return resposta(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> tratarIntegridade() {
        return resposta(HttpStatus.CONFLICT, "A operação conflita com registros existentes ou vinculados.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> tratarAcessoNegado() {
        return resposta(HttpStatus.FORBIDDEN, "Acesso não permitido.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> tratarAutenticacao() {
        return resposta(HttpStatus.UNAUTHORIZED, "Autenticação necessária ou inválida.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> tratarFalhaInesperada(Exception exception) {
        log.error("Falha inesperada ao processar a requisição", exception);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir a operação. Tente novamente mais tarde.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String mensagem = "Requisição inválida. Verifique os campos e parâmetros informados.";
        if (exception instanceof MethodArgumentNotValidException validacao) {
            mensagem = validacao.getBindingResult().getAllErrors().stream()
                    .map(erro -> erro.getDefaultMessage() == null ? "Campo inválido." : erro.getDefaultMessage())
                    .sorted().findFirst().orElse(mensagem);
        } else if (exception instanceof ResponseStatusException resposta) {
            mensagem = resposta.getReason() == null ? mensagem : resposta.getReason();
        }
        if (status.is5xxServerError()) mensagem = "Não foi possível concluir a operação. Tente novamente mais tarde.";
        return new ResponseEntity<>(Map.of("mensagem", mensagem), headers, status);
    }

    private ResponseEntity<Object> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(Map.of("mensagem", mensagem));
    }
}

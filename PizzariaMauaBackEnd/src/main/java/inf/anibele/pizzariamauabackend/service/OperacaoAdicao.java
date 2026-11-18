package inf.anibele.pizzariamauabackend.service;

import inf.anibele.pizzariamauabackend.dto.ItemPedidoRequestDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class OperacaoAdicao {
    private static final Pattern UUID_COMPLETO = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private OperacaoAdicao() {}

    public static UUID validarChave(List<String> valores) {
        if (valores == null) return null;
        if (valores.size() != 1 || !UUID_COMPLETO.matcher(valores.get(0)).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma unica Idempotency-Key no formato UUID completo.");
        }
        return UUID.fromString(valores.get(0));
    }

    // v1: UTF-8, LF, sem quebra final; lista na ordem original, inclusive repeticoes.
    public static byte[] hash(Long pedidoId, List<ItemPedidoRequestDTO> itens) {
        StringBuilder texto = new StringBuilder("v1\nPUT\n/pedidos/").append(pedidoId)
                .append("/itens\n{\"pedidoId\":").append(pedidoId).append(",\"itens\":[");
        for (int i = 0; i < itens.size(); i++) {
            if (i > 0) texto.append(',');
            var item = itens.get(i);
            texto.append("{\"produtoId\":").append(item.produtoId())
                    .append(",\"quantidade\":").append(item.quantidade()).append('}');
        }
        texto.append("]}");
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.toString().getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponivel.", exception);
        }
    }
}

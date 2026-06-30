package inf.anibele.pizzariamauabackend.mapper;

import inf.anibele.pizzariamauabackend.dto.ItemPedidoResponseDTO;
import inf.anibele.pizzariamauabackend.model.ItemPedido;
import org.springframework.stereotype.Component;

@Component
public class ItemPedidoMapper {

    public ItemPedidoResponseDTO toResponseDTO(ItemPedido item) {
        if (item == null) return null;

        // Extrai apenas os números da String "20 min" -> 20
        Integer minutos = 0;
        if (item.getProduto() != null && item.getProduto().getDescricao() != null) {
            String tempoStr = item.getProduto().getDescricao().getTempoMedioPreparo();
            if (tempoStr != null) {
                String apenasNumeros = tempoStr.replaceAll("\\D", "");
                minutos = apenasNumeros.isEmpty() ? 0 : Integer.parseInt(apenasNumeros);
            }
        }

        assert item.getProduto() != null;
        return new ItemPedidoResponseDTO(
                item.getId(),
                item.getProduto().getId(),
                item.getProduto().getNome(),
                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getStatus(),
                item.getDataHoraInclusao(),
                minutos,
                item.getDataHoraConclusao()
        );
    }
}
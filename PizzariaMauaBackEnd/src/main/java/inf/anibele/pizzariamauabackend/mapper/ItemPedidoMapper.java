package inf.anibele.pizzariamauabackend.mapper;

import inf.anibele.pizzariamauabackend.dto.ItemPedidoResponseDTO;
import inf.anibele.pizzariamauabackend.model.ItemPedido;
import inf.anibele.pizzariamauabackend.util.TempoPreparo;
import org.springframework.stereotype.Component;

@Component
public class ItemPedidoMapper {

    public ItemPedidoResponseDTO toResponseDTO(ItemPedido item) {
        if (item == null) return null;

        // Dados antigos incompatíveis mantêm a consulta disponível, com tempo desconhecido como zero.
        Integer minutos = 0;
        if (item.getProduto() != null && item.getProduto().getDescricao() != null) {
            String tempoStr = item.getProduto().getDescricao().getTempoMedioPreparo();
            Integer convertido = TempoPreparo.emMinutos(tempoStr);
            minutos = convertido == null ? 0 : convertido;
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

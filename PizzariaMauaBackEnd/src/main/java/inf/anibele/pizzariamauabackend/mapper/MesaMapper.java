package inf.anibele.pizzariamauabackend.mapper;

import inf.anibele.pizzariamauabackend.dto.MesaRequestDTO;
import inf.anibele.pizzariamauabackend.dto.MesaResponseDTO;
import inf.anibele.pizzariamauabackend.model.Mesa;
import inf.anibele.pizzariamauabackend.model.StatusMesa;
import org.springframework.stereotype.Component;

@Component
public class MesaMapper {

    // Converte a Model para o DTO de Resposta (Saída da API)
    public MesaResponseDTO toResponseDTO(Mesa mesa) {
        if (mesa == null) return null;

        return new MesaResponseDTO(
                mesa.getId(),
                mesa.getNumero(),
                mesa.getStatus()
        );
    }

    // Converte o DTO de Requisição para a Model (Cadastro inicial da mesa)
    public Mesa toEntity(MesaRequestDTO dto) {
        if (dto == null) return null;

        Mesa mesa = new Mesa();
        mesa.setNumero(dto.numero());
        // Toda mesa nova cadastrada no sistema começa, por padrão, como LIVRE
        mesa.setStatus(StatusMesa.LIVRE);

        return mesa;
    }
}
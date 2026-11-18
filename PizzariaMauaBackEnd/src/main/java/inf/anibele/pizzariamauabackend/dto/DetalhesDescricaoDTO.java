package inf.anibele.pizzariamauabackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import inf.anibele.pizzariamauabackend.util.TempoPreparo;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;

public record DetalhesDescricaoDTO(
        String breveDescricao,
        String tempoMedioPreparo,
        boolean pratoVegano,
        List<String> ingredientes
) {
    @JsonIgnore
    @AssertTrue(message = "Informe o tempo de preparo no formato '20 min', dentro do limite de minutos inteiros.")
    public boolean isTempoMedioPreparoValido() {
        return TempoPreparo.emMinutos(tempoMedioPreparo) != null;
    }
}

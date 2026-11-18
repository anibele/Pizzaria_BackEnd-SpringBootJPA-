package inf.anibele.pizzariamauabackend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

// Limites civis da pizzaria convertidos para a convencao UTC da coluna financeira.
record PeriodoRecebimento(LocalDateTime inicioUtc, LocalDateTime fimUtc) {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    static PeriodoRecebimento doDia(LocalDate dia) {
        return new PeriodoRecebimento(
                LocalDateTime.ofInstant(dia.atStartOfDay(FUSO).toInstant(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(dia.plusDays(1).atStartOfDay(FUSO).toInstant(), ZoneOffset.UTC));
    }
}

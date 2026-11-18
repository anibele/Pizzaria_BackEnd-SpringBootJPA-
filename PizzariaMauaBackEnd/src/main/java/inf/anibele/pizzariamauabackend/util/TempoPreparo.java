package inf.anibele.pizzariamauabackend.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TempoPreparo {

    private static final Pattern FORMATO = Pattern.compile("([0-9]+) min");

    private TempoPreparo() {}

    // Ausência opcional é zero; formato inválido ou overflow não tem interpretação.
    public static Integer emMinutos(String texto) {
        if (texto == null || texto.isBlank()) return 0;
        Matcher matcher = FORMATO.matcher(texto);
        if (!matcher.matches()) return null;
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}

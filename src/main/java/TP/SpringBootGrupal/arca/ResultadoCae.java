package TP.SpringBootGrupal.arca;

import java.time.LocalDate;
import java.util.List;

public record ResultadoCae(
        String resultado,
        String cae,
        LocalDate fechaVencimiento,
        List<String> observaciones
) {
}
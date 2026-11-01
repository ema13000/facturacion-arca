package TP.SpringBootGrupal.arca;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SolicitudFacturaC(
        int puntoVenta,
        long numeroComprobante,
        LocalDate fechaEmision,
        int tipoDocumento,
        long numeroDocumento,
        int condicionIvaReceptorId,
        BigDecimal importeTotal
) {
}
package TP.SpringBootGrupal.arca;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SolicitudFacturaC(
        int puntoVenta,
        long numeroComprobante,
        LocalDate fechaEmision,
        long numeroDocumento,
        int condicionIvaReceptorId,
        BigDecimal importeTotal
) {
    public int tipoDocumento() {
        return numeroDocumento == 0 ? 99 : 80; // Sin identificar o CUIT
    }
}
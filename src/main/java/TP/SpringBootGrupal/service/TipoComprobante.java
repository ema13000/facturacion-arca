package TP.SpringBootGrupal.service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Regla de ARCA para la letra de la factura.
 * - Emisor monotributista: siempre Factura C.
 * - Emisor Responsable Inscripto: Factura A a inscriptos y monotributistas; B al resto
 *   (consumidor final, exento, etc.).
 */
public final class TipoComprobante {

    // Códigos de condición frente al IVA del receptor (tabla de ARCA)
    public static final int RESPONSABLE_INSCRIPTO = 1;
    public static final int EXENTO = 4;
    public static final int CONSUMIDOR_FINAL = 5;
    public static final int MONOTRIBUTO = 6;

    /** Alícuota general de IVA para Factura A y B. */
    public static final BigDecimal ALICUOTA_IVA = new BigDecimal("21");

    private static final Set<Integer> RECIBEN_FACTURA_A = Set.of(RESPONSABLE_INSCRIPTO, MONOTRIBUTO, 13, 16);

    private TipoComprobante() {
    }

    public static String letra(boolean emisorResponsableInscripto, int codigoAfipReceptor) {
        if (!emisorResponsableInscripto) {
            return "C";
        }
        return RECIBEN_FACTURA_A.contains(codigoAfipReceptor) ? "A" : "B";
    }

    /** Las facturas viejas no tienen letra guardada: eran todas C. */
    public static String normalizar(String letra) {
        return letra == null || letra.isBlank() ? "C" : letra;
    }

    public static boolean llevaIva(String letra) {
        return !"C".equals(normalizar(letra));
    }

    public static int codigoArca(String letra) {
        return switch (normalizar(letra)) {
            case "A" -> 1;
            case "B" -> 6;
            default -> 11;
        };
    }
}

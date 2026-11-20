package TP.SpringBootGrupal.service;

import java.util.Set;

/**
 * Reglas de ARCA para un CUIT (11 dígitos, sin guiones).
 * - Los 2 primeros dígitos dicen el tipo: 20/23/24/27 persona humana; 30/33/34 empresa.
 * - El último es el dígito verificador, calculado con los 10 anteriores.
 */
public final class ReglasCuit {

    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final Set<String> PERSONAS = Set.of("20", "23", "24", "27");
    private static final Set<String> EMPRESAS = Set.of("30", "33", "34");

    private ReglasCuit() {
    }

    public static boolean esValido(String cuit) {
        if (cuit == null || !cuit.matches("\\d{11}")) {
            return false;
        }
        String tipo = cuit.substring(0, 2);
        if (!PERSONAS.contains(tipo) && !EMPRESAS.contains(tipo)) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            suma += Character.getNumericValue(cuit.charAt(i)) * PESOS[i];
        }
        int digito = 11 - suma % 11;
        if (digito == 11) {
            digito = 0;
        }
        // Con resultado 10 ARCA no usa ese prefijo: el CUIT no es válido
        return digito != 10 && digito == Character.getNumericValue(cuit.charAt(10));
    }

    public static boolean esEmpresa(String cuit) {
        return cuit != null && cuit.length() >= 2 && EMPRESAS.contains(cuit.substring(0, 2));
    }

    /** Una empresa (30/33/34) no puede ser monotributista ni consumidor final. */
    public static boolean coincideConCondicion(String cuit, int codigoCondicionIva) {
        if (!esEmpresa(cuit)) {
            return true;
        }
        return codigoCondicionIva != TipoComprobante.MONOTRIBUTO
                && codigoCondicionIva != TipoComprobante.CONSUMIDOR_FINAL;
    }
}

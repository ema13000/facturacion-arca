package TP.SpringBootGrupal.dtos.Factura;

import TP.SpringBootGrupal.entities.FacturaVenta;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FacturaReporteDTO {

    private Long id;
    // Identificación interna única, generada por la base al registrar la factura.
    private Long numeroFactura;
    private Date fechaEmision;
    private String clienteDenominacion;
    private String condicionIva;
    private String puntoVentaDescripcion;
    private double importeTotal;
    private long cantidadItems;
    private String estado;
    private String observaciones;
    // CUIT mostrado al confirmar la autorización y al consultar la factura.
    private String clienteCuit;

    // Letra del comprobante: A, B o C
    private String tipoComprobante;
    private String clienteNombreCompleto;
    private Long condicionIvaId;
    private boolean informadaArca;
    private String codigoMoneda;
    private String motivoAnulacion;

    public static FacturaReporteDTO toDTO(FacturaVenta factura) {
        var cliente = factura.getCliente();
        String denominacion = cliente == null ? "Consumidor Final" : cliente.getDenominacion();
        String cuit = cliente == null ? null : cliente.getCuit();
        String nombreCompleto = cliente == null ? "" : Stream.of(cliente.getNombre(), cliente.getApellido())
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(nombre -> !nombre.isEmpty())
                .collect(Collectors.joining(" "));
        boolean informadaArca = "AUTORIZADA".equals(factura.getEstado()) || "ANULADA".equals(factura.getEstado());
        String moneda = factura.getTipoMoneda() == null ? null : factura.getTipoMoneda().getCodigoAfip();

        return new FacturaReporteDTO(
                factura.getId(),
                factura.getId(),
                factura.getFechaEmision(),
                denominacion,
                factura.getCondicionIva().getDenominacion(),
                factura.getPuntoVenta().getDescripcion(),
                factura.getImporteTotal(),
                factura.getDetalles().size(),
                factura.getEstado(),
                factura.getObservaciones(),
                cuit,
                TP.SpringBootGrupal.service.TipoComprobante.normalizar(factura.getTipoComprobante()),
                nombreCompleto,
                factura.getCondicionIva().getId(),
                informadaArca,
                moneda,
                motivoAnulacion(factura)
        );
    }

    private static String motivoAnulacion(FacturaVenta factura) {
        if (!"ANULADA".equals(factura.getEstado())) return null;
        String motivo = factura.getMotivoAnulacion();
        if (motivo != null) return motivo.isBlank() ? null : motivo;

        // Antes el motivo se guardaba al final de las observaciones.
        String observaciones = factura.getObservaciones();
        if (observaciones == null) return null;

        String prefijo = "Anulada: ";
        String separador = " | " + prefijo;
        int inicio = observaciones.indexOf(separador);
        if (inicio >= 0) {
            motivo = observaciones.substring(inicio + separador.length()).strip();
        } else if (observaciones.startsWith(prefijo)) {
            motivo = observaciones.substring(prefijo.length()).strip();
        }
        return motivo == null || motivo.isEmpty() || "sin motivo".equals(motivo) ? null : motivo;
    }
}
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

    public static FacturaReporteDTO toDTO(FacturaVenta factura) {
        var cliente = factura.getCliente();
        String denominacion = cliente == null ? "Consumidor Final" : cliente.getDenominacion();
        String cuit = cliente == null ? null : cliente.getCuit();
        String nombreCompleto = cliente == null ? "" : Stream.of(cliente.getNombre(), cliente.getApellido())
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(nombre -> !nombre.isEmpty())
                .collect(Collectors.joining(" "));
        boolean informadaArca = tieneValor(factura.getResultadoAfip()) || tieneValor(factura.getCae());
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
                moneda
        );
    }

    private static boolean tieneValor(String valor) {
        return valor != null && !valor.isBlank();
    }
}
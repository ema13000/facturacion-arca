package TP.SpringBootGrupal.dtos.Factura;

import TP.SpringBootGrupal.entities.FacturaVenta;

import java.util.Date;
import java.util.List;

/** Datos necesarios para imprimir una factura autorizada (incluye CAE y su vencimiento). */
public record FacturaImpresionDTO(
        Long id,
        Long numeroFactura,
        int puntoVentaNumero,
        String puntoVentaDescripcion,
        Date fechaEmision,
        String clienteDenominacion,
        String clienteCuitCuil,
        String condicionIva,
        double importeTotal,
        String cae,
        Date caeFechaVencimiento,
        List<Renglon> detalles
) {
    public record Renglon(
            String descripcion,
            double cantidad,
            double precioUnitario,
            double porcentajeBonificacion,
            double importeSubtotal
    ) {
    }

    public static FacturaImpresionDTO toDTO(FacturaVenta factura) {
        var cliente = factura.getCliente();
        var punto = factura.getPuntoVenta();

        return new FacturaImpresionDTO(
                factura.getId(),
                factura.getNumero(),
                punto.getNumero(),
                punto.getDescripcion(),
                factura.getFechaEmision(),
                cliente == null ? "Consumidor Final" : cliente.getDenominacion(),
                cliente == null ? null : cliente.getCuitCuil(),
                factura.getCondicionIva().getDenominacion(),
                factura.getImporteTotal(),
                factura.getCae(),
                factura.getCaeFechaVencimiento(),
                factura.getDetalles().stream()
                        .map(d -> new Renglon(d.getDescripcion(), d.getCantidad(),
                                d.getPrecioUnitario(), d.getPorcentajeBonificacion(),
                                d.getImporteSubtotal()))
                        .toList()
        );
    }
}

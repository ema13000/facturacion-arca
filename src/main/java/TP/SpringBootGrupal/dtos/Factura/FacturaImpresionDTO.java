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
        String clienteCuit,
        String condicionIva,
        double importeTotal,
        String cae,
        Date caeFechaVencimiento,
        List<Renglon> detalles,
        String tipoComprobante,
        double importeNeto,
        double importeIva,
        String observaciones
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
                cliente == null ? null : cliente.getCuit(),
                factura.getCondicionIva().getDenominacion(),
                factura.getImporteTotal(),
                factura.getCae(),
                factura.getCaeFechaVencimiento(),
                factura.getDetalles().stream()
                        .map(d -> new Renglon(d.getDescripcion(), d.getCantidad(),
                                d.getPrecioUnitario(), d.getPorcentajeBonificacion(),
                                d.getImporteSubtotal()))
                        .toList(),
                TP.SpringBootGrupal.service.TipoComprobante.normalizar(factura.getTipoComprobante()),
                redondear(factura.getDetalles().stream().mapToDouble(d -> d.getImporteNeto()).sum()),
                redondear(factura.getDetalles().stream().mapToDouble(d -> d.getImporteIva()).sum()),
                factura.getObservaciones()
        );
    }

    private static double redondear(double importe) {
        return Math.round(importe * 100) / 100.0;
    }
}

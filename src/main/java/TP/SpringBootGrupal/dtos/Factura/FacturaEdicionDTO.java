package TP.SpringBootGrupal.dtos.Factura;

import TP.SpringBootGrupal.dtos.FacturaDetalle.FacturaDetalleRequestDTO;
import TP.SpringBootGrupal.entities.FacturaVenta;

import java.util.List;

public record FacturaEdicionDTO(
        Long numeroFactura,
        Long clienteId,
        Long condicionIvaId,
        Long tipoMonedaId,
        Long puntoVentaId,
        List<FacturaDetalleRequestDTO> detalles,
        java.time.LocalDate fechaEmision,
        String observaciones
) {
    public static FacturaEdicionDTO toDTO(FacturaVenta factura) {
        List<FacturaDetalleRequestDTO> detalles = factura.getDetalles()
                .stream()
                .map(detalle -> new FacturaDetalleRequestDTO(
                        detalle.getListaPrecioArticulo().getId(),
                        detalle.getCantidad(),
                        detalle.getPorcentajeBonificacion()
                ))
                .toList();

        return new FacturaEdicionDTO(
                factura.getId(),
                factura.getCliente() == null
                        ? null
                        : factura.getCliente().getId(),
                factura.getCondicionIva().getId(),
                factura.getTipoMoneda().getId(),
                factura.getPuntoVenta().getId(),
                detalles,
                factura.getFechaEmision() == null
                        ? null
                        : new java.sql.Date(factura.getFechaEmision().getTime()).toLocalDate(),
                factura.getObservaciones()
        );
    }
}

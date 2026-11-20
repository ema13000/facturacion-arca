package TP.SpringBootGrupal.dtos.Factura;

import TP.SpringBootGrupal.dtos.FacturaDetalle.FacturaDetalleRequestDTO;
import TP.SpringBootGrupal.entities.FacturaVenta;

import java.util.List;

public record FacturaRequestDTO(
        Long clienteId,
        Long condicionIvaId,
        Long tipoMonedaId,
        Long puntoVentaId,
        List<FacturaDetalleRequestDTO> detalles,
        // Fecha del comprobante (null = hoy) y observaciones libres (opcionales)
        java.time.LocalDate fechaEmision,
        String observaciones
) {
    public FacturaRequestDTO(Long clienteId, Long condicionIvaId, Long tipoMonedaId, Long puntoVentaId,
                             List<FacturaDetalleRequestDTO> detalles) {
        this(clienteId, condicionIvaId, tipoMonedaId, puntoVentaId, detalles, null, null);
    }
}
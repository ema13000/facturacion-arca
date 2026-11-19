package TP.SpringBootGrupal.dtos.Factura;

import TP.SpringBootGrupal.entities.FacturaVenta;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FacturaReporteDTO {

    private Long id;
    private Long numeroFactura;
    private Date fechaEmision;
    private String clienteDenominacion;
    private String condicionIva;
    private String puntoVentaDescripcion;
    private double importeTotal;
    private long cantidadItems;
    private String estado;
    private String observaciones;
    // CUIT/CUIL del cliente (null si es consumidor final): se usa al autorizar en ARCA
    private String clienteCuitCuil;

    public static FacturaReporteDTO toDTO(FacturaVenta factura) {
        String cliente = factura.getCliente() == null
                ? "Consumidor Final"
                : factura.getCliente().getDenominacion();
        String cuit = factura.getCliente() == null
                ? null
                : factura.getCliente().getCuitCuil();

        return new FacturaReporteDTO(
                factura.getId(),
                factura.getNumero(),
                factura.getFechaEmision(),
                cliente,
                factura.getCondicionIva().getDenominacion(),
                factura.getPuntoVenta().getDescripcion(),
                factura.getImporteTotal(),
                factura.getDetalles().size(),
                factura.getEstado(),
                factura.getObservaciones(),
                cuit
        );
    }
}
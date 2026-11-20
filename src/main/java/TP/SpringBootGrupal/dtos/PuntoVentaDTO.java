package TP.SpringBootGrupal.dtos;

/** Punto de venta con la condición del emisor: define si se emite Factura C o A/B. */
public record PuntoVentaDTO(Long id, String descripcion, boolean responsableInscripto) {
}

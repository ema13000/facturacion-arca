package TP.SpringBootGrupal.dtos;

public record PrecioArticuloExplorerDTO(
        Long id,
        String articuloCodigo,
        String articuloDenominacion,
        String listaPrecioDenominacion,
        double precioVenta,
        Long listaPrecioId
) {
}

package TP.SpringBootGrupal.dtos;

public record ClienteExplorerDTO(
        Long id,
        String cuit,
        String denominacion,
        String nombre,
        String apellido,
        // Categoría frente al IVA (null si el cliente no la tiene cargada)
        Long condicionIvaId,
        String condicionIva,
        Integer condicionIvaCodigo
) {
}

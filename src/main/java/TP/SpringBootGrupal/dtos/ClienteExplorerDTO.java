package TP.SpringBootGrupal.dtos;

public record ClienteExplorerDTO(
        Long id,
        String cuitCuil,
        String denominacion,
        String nombre,
        String apellido
) {
}

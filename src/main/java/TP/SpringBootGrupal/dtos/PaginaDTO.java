package TP.SpringBootGrupal.dtos;

import java.util.List;

/** Una página de resultados: el contenido y los datos para paginar (la página empieza en 0). */
public record PaginaDTO<T>(List<T> contenido, int pagina, int totalPaginas, long totalElementos) {
}

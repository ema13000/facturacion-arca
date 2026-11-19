package TP.SpringBootGrupal.repository;

import TP.SpringBootGrupal.entities.ListaPrecioArticulo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ListaPrecioArticuloRepository extends JpaRepository<ListaPrecioArticulo, Long> {
    List<ListaPrecioArticulo> findByArticulo_Id(Long articuloId);

    // Busca artículos de UNA lista de precios por código o denominación
    @Query("""
            select p from ListaPrecioArticulo p
            join fetch p.articulo a
            join fetch p.listaPrecio l
            where l.id = :listaPrecioId
              and p.fechaBaja is null and a.fechaBaja is null and l.fechaBaja is null
              and (lower(a.denominacion) like lower(concat('%', :texto, '%'))
                   or lower(a.codigo) like lower(concat('%', :texto, '%')))
            order by a.denominacion
            """)
    List<ListaPrecioArticulo> buscar(
            @Param("listaPrecioId") Long listaPrecioId,
            @Param("texto") String texto,
            Pageable pagina
    );
}

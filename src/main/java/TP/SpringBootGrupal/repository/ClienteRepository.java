package TP.SpringBootGrupal.repository;

import TP.SpringBootGrupal.entities.Cliente;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    // Busca en la base por razón social, nombre, apellido o CUIT/CUIL (solo clientes activos)
    @Query("""
            select c from Cliente c
            where c.fechaBaja is null
              and (lower(c.denominacion) like lower(concat('%', :texto, '%'))
                   or lower(coalesce(c.nombre, '')) like lower(concat('%', :texto, '%'))
                   or lower(coalesce(c.apellido, '')) like lower(concat('%', :texto, '%'))
                   or c.cuitCuil like concat('%', :texto, '%'))
            order by c.denominacion
            """)
    List<Cliente> buscar(@Param("texto") String texto, Pageable pagina);

    java.util.Optional<Cliente> findFirstByCuitCuil(String cuitCuil);
}

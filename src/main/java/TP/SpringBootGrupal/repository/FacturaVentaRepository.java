package TP.SpringBootGrupal.repository;

import TP.SpringBootGrupal.entities.FacturaVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacturaVentaRepository extends JpaRepository<FacturaVenta,Long> {

    // Último número usado en el punto de venta (0 si todavía no hay facturas)
    @Query("select coalesce(max(f.numero), 0) from FacturaVenta f where f.puntoVenta.id = :puntoVentaId")
    long obtenerUltimoNumero(@Param("puntoVentaId") Long puntoVentaId);
}

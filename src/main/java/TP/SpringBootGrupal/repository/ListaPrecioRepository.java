package TP.SpringBootGrupal.repository;

import TP.SpringBootGrupal.entities.ListaPrecio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListaPrecioRepository extends JpaRepository<ListaPrecio, Long> {
    List<ListaPrecio> findByFechaBajaIsNullOrderByDenominacion();
}

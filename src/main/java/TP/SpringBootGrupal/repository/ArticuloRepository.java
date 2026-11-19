package TP.SpringBootGrupal.repository;

import TP.SpringBootGrupal.entities.Articulo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticuloRepository extends JpaRepository<Articulo, Long> {
    boolean existsByCodigo(String codigo);
}

package TP.SpringBootGrupal.repository;

import TP.SpringBootGrupal.entities.Contacto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactoRepository extends JpaRepository<Contacto, Long> {
}

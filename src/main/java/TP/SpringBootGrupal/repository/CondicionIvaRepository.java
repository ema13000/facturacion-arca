package TP.SpringBootGrupal.repository;

import TP.SpringBootGrupal.entities.Cliente;
import TP.SpringBootGrupal.entities.CondicionIva;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CondicionIvaRepository extends JpaRepository<CondicionIva, Long> {
    java.util.Optional<CondicionIva> findFirstByCodigoAfip(int codigoAfip);
}
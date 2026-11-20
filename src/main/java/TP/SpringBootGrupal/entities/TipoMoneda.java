package TP.SpringBootGrupal.entities;

import lombok.*;
import lombok.experimental.SuperBuilder;
import jakarta.persistence.*;

@Entity
@Table (name= "tipo_moneda")
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)

public class TipoMoneda extends AuditoriaApp {
    @Column (nullable = false)
    private String codigoAfip;
    @Column (nullable = false)
    private String denominacion;
    @Column (nullable = false)
    private String simbolo;

    public boolean esDolarEstadounidense() {
        return "DOL".equalsIgnoreCase(codigoAfip) || "USD".equalsIgnoreCase(codigoAfip);
    }
}

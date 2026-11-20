package TP.SpringBootGrupal.entities;

import lombok.*;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "punto_venta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)


public class PuntoVenta extends AuditoriaApp {
    @Column(nullable = false)
    @EqualsAndHashCode.Include
    private int numero;
    private String descripcion;
    private String tipoEmision;
    private String domicilioComercial;

    // Condición del emisor en este punto de venta: true = Responsable Inscripto (emite A/B),
    // false o null = Monotributista (emite C)
    private Boolean responsableInscripto;
}

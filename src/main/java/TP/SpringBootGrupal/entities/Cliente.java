package TP.SpringBootGrupal.entities;

import lombok.*;

import jakarta.persistence.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@SuperBuilder
public class Cliente extends AuditoriaApp {
    @Column(nullable = false)
    @EqualsAndHashCode.Include
    private String cuitCuil;
    @Column(nullable = false)
    private String denominacion;
    // Nombre y apellido de la persona (o del contacto, si es una empresa). Opcionales.
    private String nombre;
    private String apellido;

    // Categoría frente al IVA del cliente (fija: el formulario de factura no la deja cambiar)
    @ManyToOne
    @JoinColumn(name = "condicion_iva_id")
    private CondicionIva condicionIva;
    @OneToOne
    @JoinColumn(name = "contacto_id", nullable = false)
    private Contacto contacto;
    @OneToOne
    @JoinColumn(name= "domicilio_id", nullable = false)
    private Domicilio domicilio;
}

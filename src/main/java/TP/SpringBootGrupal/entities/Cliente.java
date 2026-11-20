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
    // Conserva la columna existente para los clientes ya registrados.
    @Column(name = "cuit_cuil", nullable = false)
    @EqualsAndHashCode.Include
    private String cuit;
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

    @PrePersist
    @PreUpdate
    private void guardarCuitNormalizado() {
        cuit = cuitNormalizado();
    }

    public long numeroCuit() {
        return Long.parseLong(cuitNormalizado());
    }

    private String cuitNormalizado() {
        String valor = cuit == null ? "" : cuit.strip();
        if (!valor.matches("[1-9][0-9]{10}|[1-9][0-9]-[0-9]{8}-[0-9]")) {
            throw new IllegalStateException("El CUIT del cliente debe tener 11 dígitos");
        }
        return valor.replace("-", "");
    }
}

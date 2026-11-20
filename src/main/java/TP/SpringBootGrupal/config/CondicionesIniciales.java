package TP.SpringBootGrupal.config;

import TP.SpringBootGrupal.entities.CondicionIva;
import TP.SpringBootGrupal.entities.PuntoVenta;
import TP.SpringBootGrupal.entities.Usuario;
import TP.SpringBootGrupal.repository.CondicionIvaRepository;
import TP.SpringBootGrupal.repository.PuntoVentaRepository;
import TP.SpringBootGrupal.repository.UsuarioRepository;
import TP.SpringBootGrupal.service.TipoComprobante;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deja cargadas las condiciones frente al IVA y la condición del emisor de cada punto de venta.
 * Corre antes que los clientes de prueba, que necesitan estas condiciones.
 */
@Component
@Order(1)
public class CondicionesIniciales implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CondicionesIniciales.class);

    /** Código de ARCA → nombre de la condición. */
    public static final Map<Integer, String> CONDICIONES = new LinkedHashMap<>();

    static {
        CONDICIONES.put(TipoComprobante.RESPONSABLE_INSCRIPTO, "IVA Responsable Inscripto");
        CONDICIONES.put(TipoComprobante.EXENTO, "IVA Sujeto Exento");
        CONDICIONES.put(TipoComprobante.CONSUMIDOR_FINAL, "Consumidor Final");
        CONDICIONES.put(TipoComprobante.MONOTRIBUTO, "Responsable Monotributo");
    }

    private final CondicionIvaRepository condicionIvaRepository;
    private final PuntoVentaRepository puntoVentaRepository;
    private final UsuarioRepository usuarioRepository;

    public CondicionesIniciales(
            CondicionIvaRepository condicionIvaRepository,
            PuntoVentaRepository puntoVentaRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.condicionIvaRepository = condicionIvaRepository;
        this.puntoVentaRepository = puntoVentaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Usuario> usuarios = usuarioRepository.findAll();
        if (usuarios.isEmpty()) {
            log.warn("No hay usuarios cargados: no se pueden crear las condiciones de IVA");
            return;
        }
        Usuario usuario = usuarios.get(0);
        Date ahora = new Date();

        CONDICIONES.forEach((codigo, nombre) -> {
            if (condicionIvaRepository.findFirstByCodigoAfip(codigo).isEmpty()) {
                CondicionIva condicion = new CondicionIva();
                condicion.setCodigoAfip(codigo);
                condicion.setDenominacion(nombre);
                auditar(condicion, usuario, ahora);
                condicionIvaRepository.save(condicion);
                log.info("Se creó la condición de IVA {}", nombre);
            }
        });

        prepararPuntosDeVenta(usuario, ahora);
    }

    private void prepararPuntosDeVenta(Usuario usuario, Date ahora) {
        List<PuntoVenta> puntos = puntoVentaRepository.findAll();

        // Los puntos que ya existían son del emisor monotributista (Factura C)
        for (PuntoVenta punto : puntos) {
            if (punto.getResponsableInscripto() == null) {
                punto.setResponsableInscripto(false);
                puntoVentaRepository.save(punto);
            }
        }

        boolean hayInscripto = puntos.stream().anyMatch(p -> Boolean.TRUE.equals(p.getResponsableInscripto()));
        if (hayInscripto) {
            return;
        }

        int siguienteNumero = puntos.stream().mapToInt(PuntoVenta::getNumero).max().orElse(0) + 1;
        PuntoVenta inscripto = new PuntoVenta();
        inscripto.setNumero(siguienteNumero);
        inscripto.setDescripcion("Sucursal Responsable Inscripto");
        inscripto.setResponsableInscripto(true);
        auditar(inscripto, usuario, ahora);
        puntoVentaRepository.save(inscripto);
        log.info("Se creó el punto de venta {} (Responsable Inscripto) para Factura A y B", siguienteNumero);
    }

    private void auditar(TP.SpringBootGrupal.entities.AuditoriaApp entidad, Usuario usuario, Date ahora) {
        entidad.setFechaAlta(ahora);
        entidad.setFechaModificacion(ahora);
        entidad.setUsuarioCarga(usuario);
        entidad.setUsuarioModificacion(usuario);
    }
}

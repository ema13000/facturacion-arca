package TP.SpringBootGrupal.config;

import TP.SpringBootGrupal.entities.AuditoriaApp;
import TP.SpringBootGrupal.entities.CondicionIva;
import TP.SpringBootGrupal.entities.PuntoVenta;
import TP.SpringBootGrupal.entities.TipoMoneda;
import TP.SpringBootGrupal.entities.Usuario;
import TP.SpringBootGrupal.repository.CondicionIvaRepository;
import TP.SpringBootGrupal.repository.PuntoVentaRepository;
import TP.SpringBootGrupal.repository.TipoMonedaRepository;
import TP.SpringBootGrupal.repository.UsuarioRepository;
import TP.SpringBootGrupal.service.TipoComprobante;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga las condiciones de IVA, las monedas y los puntos de venta de ejemplo.
 * Corre antes que los clientes de prueba, que necesitan estas condiciones.
 */
@Component
@Order(1)
public class CondicionesIniciales implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CondicionesIniciales.class);
    private static final List<String> SUCURSALES_DE_EJEMPLO = List.of(
            "Sucursal Los Álamos", "Sucursal La Arboleda", "Sucursal Los Tilos");

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
    private final TipoMonedaRepository tipoMonedaRepository;
    private final UsuarioRepository usuarioRepository;

    public CondicionesIniciales(
            CondicionIvaRepository condicionIvaRepository,
            PuntoVentaRepository puntoVentaRepository,
            TipoMonedaRepository tipoMonedaRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.condicionIvaRepository = condicionIvaRepository;
        this.puntoVentaRepository = puntoVentaRepository;
        this.tipoMonedaRepository = tipoMonedaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Usuario> usuarios = usuarioRepository.findAll();
        if (usuarios.isEmpty()) {
            log.warn("No hay usuarios cargados: no se pueden crear los catálogos iniciales");
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
        if (tipoMonedaRepository.findAll().stream().noneMatch(moneda -> "PES".equals(moneda.getCodigoAfip()))) {
            crearMoneda("PES", "Peso argentino", "$", usuario, ahora);
        }
    }

    private void prepararPuntosDeVenta(Usuario usuario, Date ahora) {
        List<PuntoVenta> puntos = puntoVentaRepository.findAll();

        List<PuntoVenta> activos = puntos.stream()
                .filter(punto -> punto.getFechaBaja() == null)
                .sorted(Comparator.comparingInt(PuntoVenta::getNumero))
                .toList();
        Deque<String> nombresDisponibles = new ArrayDeque<>(SUCURSALES_DE_EJEMPLO);
        nombresDisponibles.removeAll(activos.stream().map(PuntoVenta::getDescripcion).toList());
        for (PuntoVenta punto : activos) {
            // Reconoce el ejemplo de la carga anterior sin tocar los puntos configurados por el usuario.
            boolean ejemploAnterior = Boolean.TRUE.equals(punto.getResponsableInscripto())
                    && "Sucursal Responsable Inscripto".equals(punto.getDescripcion())
                    && punto.getTipoEmision() == null && punto.getDomicilioComercial() == null;
            boolean ejemploNumerado = Boolean.FALSE.equals(punto.getResponsableInscripto())
                    && ("Sucursal " + punto.getNumero()).equals(punto.getDescripcion())
                    && "ELECTRONICA".equals(punto.getTipoEmision()) && punto.getDomicilioComercial() == null;
            if (ejemploAnterior || ejemploNumerado) {
                punto.setDescripcion(nombreSucursal(nombresDisponibles, punto.getNumero()));
                punto.setTipoEmision("ELECTRONICA");
            }
            if (ejemploAnterior || ejemploNumerado || punto.getResponsableInscripto() == null) {
                punto.setResponsableInscripto(false);
                punto.setFechaModificacion(ahora);
                punto.setUsuarioModificacion(usuario);
                puntoVentaRepository.save(punto);
            }
        }

        long monotributistas = activos.stream().filter(punto -> Boolean.FALSE.equals(punto.getResponsableInscripto())).count();
        int siguienteNumero = puntos.stream().mapToInt(PuntoVenta::getNumero).max().orElse(0) + 1;
        for (long i = monotributistas; i < SUCURSALES_DE_EJEMPLO.size(); i++) {
            PuntoVenta punto = new PuntoVenta();
            punto.setNumero(siguienteNumero++);
            punto.setDescripcion(nombreSucursal(nombresDisponibles, punto.getNumero()));
            punto.setTipoEmision("ELECTRONICA");
            punto.setResponsableInscripto(false);
            auditar(punto, usuario, ahora);
            puntoVentaRepository.save(punto);
            log.info("Se creó el punto de venta {} de ejemplo para Factura C", punto.getNumero());
        }
    }

    private String nombreSucursal(Deque<String> nombres, int numero) {
        String disponible = nombres.pollFirst();
        return disponible == null ? "Sucursal Los Olivos " + numero : disponible;
    }

    private void crearMoneda(String codigo, String nombre, String simbolo, Usuario usuario, Date ahora) {
        TipoMoneda moneda = new TipoMoneda();
        moneda.setCodigoAfip(codigo);
        moneda.setDenominacion(nombre);
        moneda.setSimbolo(simbolo);
        auditar(moneda, usuario, ahora);
        tipoMonedaRepository.save(moneda);
        log.info("Se creó la moneda {}", codigo);
    }

    private void auditar(AuditoriaApp entidad, Usuario usuario, Date ahora) {
        entidad.setFechaAlta(ahora);
        entidad.setFechaModificacion(ahora);
        entidad.setUsuarioCarga(usuario);
        entidad.setUsuarioModificacion(usuario);
    }
}

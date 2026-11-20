package TP.SpringBootGrupal.config;

import TP.SpringBootGrupal.entities.Cliente;
import TP.SpringBootGrupal.entities.Contacto;
import TP.SpringBootGrupal.entities.Domicilio;
import TP.SpringBootGrupal.entities.Usuario;
import TP.SpringBootGrupal.entities.CondicionIva;
import TP.SpringBootGrupal.repository.ClienteRepository;
import TP.SpringBootGrupal.repository.CondicionIvaRepository;
import TP.SpringBootGrupal.repository.ContactoRepository;
import TP.SpringBootGrupal.repository.DomicilioRepository;
import TP.SpringBootGrupal.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Carga al arrancar los clientes de prueba que todavía no existen.
 * Así el buscador de clientes siempre tiene datos para probar.
 */
@Component
@Order(2)
public class DatosIniciales implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    private static final Map<Integer, String> CUITS_PRUEBA_POR_CONDICION = Map.of(
            1, "27381112220",
            5, "20231813285",
            6, "20417364774"
    );

    /** CUIT original, razón social, nombre, apellido y código ARCA de la condición de IVA. */
    public static final List<String[]> CLIENTES_PRUEBA = List.of(
            new String[]{"20123456786", "Librería Centro", "Juan", "Pérez", "6"},
            new String[]{"27234567891", "Escuela Norte", "María", "González", "1"},
            new String[]{"30712345671", "Distribuidora del Sur S.A.", "Carlos", "Rodríguez", "1"},
            new String[]{"20345678906", "Kiosco La Esquina", "Lucas", "Fernández", "6"},
            new String[]{"33698765432", "Papelera Mendoza S.R.L.", "Ana", "Martínez", "1"},
            new String[]{"20287654325", "Ferretería El Tornillo", "Diego", "López", "6"},
            new String[]{"27314567892", "Panadería Doña Rosa", "Rosa", "Sánchez", "6"},
            new String[]{"30709988774", "Constructora Andes S.A.", "Martín", "Gómez", "1"},
            new String[]{"20401112228", "Taller Mecánico Ruta 40", "Pablo", "Díaz", "6"},
            new String[]{"27253334458", "Estudio Contable Torres", "Laura", "Torres", "1"},
            new String[]{"30711223343", "Supermercado Cuyo S.R.L.", "Jorge", "Ramírez", "1"},
            new String[]{"20334445551", "Imprenta Rápida", "Federico", "Álvarez", "6"},
            new String[]{"27387651239", "Farmacia San José", "Valeria", "Romero", "1"},
            new String[]{"30695432107", "Bodega Los Olivos S.A.", "Ricardo", "Suárez", "1"},
            new String[]{"20412223331", "Gimnasio Activo", "Nicolás", "Benítez", "6"},
            new String[]{"27291118882", "Veterinaria Patitas", "Carolina", "Acosta", "6"},
            new String[]{"30715556665", "Transportes del Oeste S.R.L.", "Gustavo", "Medina", "1"},
            new String[]{"20367778882", "Verdulería La Huerta", "Sergio", "Herrera", "6"},
            new String[]{"27423331114", "Academia de Inglés Smart", "Florencia", "Castro", "1"},
            new String[]{"30704449999", "Electrónica Mendoza S.A.", "Andrés", "Molina", "1"},
            // Personas del equipo, cada una con su categoría fija
            new String[]{"27381112220", "Sofía Indovino", "Sofía", "Indovino", "1"},
            new String[]{"20352223337", "Juan Cruz Grivel", "Juan Cruz", "Grivel", "5"},
            new String[]{"20216874561", "Emanuel Mateo", "Emanuel", "Mateo", "6"},
            new String[]{"20405556678", "Enzo Mellimaci", "Enzo", "Mellimaci", "1"},
            new String[]{"20368889998", "Juan Sebastián Cabrera", "Juan Sebastián", "Cabrera", "1"}
    );

    /**
     * CUIT de prueba que se habían cargado con el dígito verificador mal calculado → CUIT corregido.
     * Al arrancar, los clientes que todavía tienen el viejo pasan al corregido (no se duplican).
     */
    public static final Map<String, String> CUITS_CORREGIDOS = Map.of(
            "30712345674", "30712345671",
            "20345678905", "20345678906",
            "33698765439", "33698765432"
    );

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContactoRepository contactoRepository;
    private final DomicilioRepository domicilioRepository;
    private final CondicionIvaRepository condicionIvaRepository;

    public DatosIniciales(
            ClienteRepository clienteRepository,
            UsuarioRepository usuarioRepository,
            ContactoRepository contactoRepository,
            DomicilioRepository domicilioRepository,
            CondicionIvaRepository condicionIvaRepository
    ) {
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.contactoRepository = contactoRepository;
        this.domicilioRepository = domicilioRepository;
        this.condicionIvaRepository = condicionIvaRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        corregirCuits();
        completarNombresFaltantes();

        List<String[]> faltantes = CLIENTES_PRUEBA.stream()
                .filter(datos -> buscarClienteDePrueba(datos).isEmpty())
                .toList();
        if (faltantes.isEmpty()) {
            return;
        }

        List<Usuario> usuarios = usuarioRepository.findAll();
        if (usuarios.isEmpty()) {
            log.warn("No hay usuarios cargados: no se pueden crear los clientes de prueba");
            return;
        }

        Usuario usuario = usuarios.get(0);
        Date ahora = new Date();

        for (String[] datos : faltantes) {
            int indice = CLIENTES_PRUEBA.indexOf(datos) + 1;
            Contacto contacto = new Contacto();
            contacto.setEmail(correoDePrueba(indice));
            contacto.setCelular("261555000" + indice);
            contacto = contactoRepository.save(contacto);

            Domicilio domicilio = new Domicilio();
            domicilio.setNombreCalle("San Martín");
            domicilio.setNumeroCalle(String.valueOf(100 * indice));
            domicilio = domicilioRepository.save(domicilio);

            Cliente cliente = new Cliente();
            cliente.setCuit(CUITS_PRUEBA_POR_CONDICION.getOrDefault(Integer.parseInt(datos[4]), datos[0]));
            cliente.setDenominacion(datos[1]);
            cliente.setNombre(datos[2]);
            cliente.setApellido(datos[3]);
            condicion(datos).ifPresent(cliente::setCondicionIva);
            cliente.setContacto(contacto);
            cliente.setDomicilio(domicilio);
            cliente.setFechaAlta(ahora);
            cliente.setFechaModificacion(ahora);
            cliente.setUsuarioCarga(usuario);
            cliente.setUsuarioModificacion(usuario);
            clienteRepository.save(cliente);
        }

        log.info("Se cargaron {} clientes de prueba", faltantes.size());
    }

    private Optional<Cliente> buscarClienteDePrueba(String[] datos) {
        // El correo y la denominación siguen identificando el ejemplo cuando se cambia su CUIT.
        return clienteRepository.findFirstByDenominacionAndContacto_Email(
                        datos[1], correoDePrueba(CLIENTES_PRUEBA.indexOf(datos) + 1))
                .or(() -> clienteRepository.findFirstByDenominacionAndCuit(datos[1], datos[0]));
    }

    private String correoDePrueba(int indice) {
        return "cliente" + indice + "@ejemplo.com";
    }

    private Optional<CondicionIva> condicion(String[] datos) {
        return condicionIvaRepository.findFirstByCodigoAfip(Integer.parseInt(datos[4]));
    }

    private void corregirCuits() {
        CUITS_CORREGIDOS.forEach((viejo, corregido) -> {
            if (clienteRepository.findFirstByCuit(corregido).isPresent()) {
                return;
            }
            clienteRepository.findFirstByCuit(viejo).ifPresent(cliente -> {
                cliente.setCuit(corregido);
                clienteRepository.save(cliente);
                log.info("Se corrigió el CUIT {} → {} ({})", viejo, corregido, cliente.getDenominacion());
            });
        });
    }

    /** Completa nombre/apellido y categoría de IVA de los clientes de prueba que ya estaban cargados. */
    private void completarNombresFaltantes() {
        for (String[] datos : CLIENTES_PRUEBA) {
            buscarClienteDePrueba(datos).ifPresent(cliente -> {
                boolean cambio = false;
                if (cliente.getNombre() == null || cliente.getNombre().isBlank()) {
                    cliente.setNombre(datos[2]);
                    cliente.setApellido(datos[3]);
                    cambio = true;
                }
                if (cliente.getCondicionIva() == null) {
                    Optional<CondicionIva> condicion = condicion(datos);
                    if (condicion.isPresent()) {
                        cliente.setCondicionIva(condicion.get());
                        cambio = true;
                    }
                }
                if (cambio) {
                    clienteRepository.save(cliente);
                }
            });
        }
    }
}

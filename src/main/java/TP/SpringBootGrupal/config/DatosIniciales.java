package TP.SpringBootGrupal.config;

import TP.SpringBootGrupal.entities.Cliente;
import TP.SpringBootGrupal.entities.Contacto;
import TP.SpringBootGrupal.entities.Domicilio;
import TP.SpringBootGrupal.entities.Usuario;
import TP.SpringBootGrupal.repository.ClienteRepository;
import TP.SpringBootGrupal.repository.ContactoRepository;
import TP.SpringBootGrupal.repository.DomicilioRepository;
import TP.SpringBootGrupal.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Carga al arrancar los clientes de prueba que todavía no existen (se buscan por CUIT/CUIL).
 * Así el buscador de clientes siempre tiene datos para probar.
 */
@Component
public class DatosIniciales implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    /** CUIT/CUIL de 11 dígitos, razón social, nombre y apellido de cada cliente de prueba. */
    public static final List<String[]> CLIENTES_PRUEBA = List.of(
            new String[]{"20123456786", "Librería Centro", "Juan", "Pérez"},
            new String[]{"27234567891", "Escuela Norte", "María", "González"},
            new String[]{"30712345674", "Distribuidora del Sur S.A.", "Carlos", "Rodríguez"},
            new String[]{"20345678905", "Kiosco La Esquina", "Lucas", "Fernández"},
            new String[]{"33698765439", "Papelera Mendoza S.R.L.", "Ana", "Martínez"},
            new String[]{"20287654325", "Ferretería El Tornillo", "Diego", "López"},
            new String[]{"27314567892", "Panadería Doña Rosa", "Rosa", "Sánchez"},
            new String[]{"30709988774", "Constructora Andes S.A.", "Martín", "Gómez"},
            new String[]{"20401112228", "Taller Mecánico Ruta 40", "Pablo", "Díaz"},
            new String[]{"27253334458", "Estudio Contable Torres", "Laura", "Torres"},
            new String[]{"30711223343", "Supermercado Cuyo S.R.L.", "Jorge", "Ramírez"},
            new String[]{"20334445551", "Imprenta Rápida", "Federico", "Álvarez"},
            new String[]{"27387651239", "Farmacia San José", "Valeria", "Romero"},
            new String[]{"30695432107", "Bodega Los Olivos S.A.", "Ricardo", "Suárez"},
            new String[]{"20412223331", "Gimnasio Activo", "Nicolás", "Benítez"},
            new String[]{"27291118882", "Veterinaria Patitas", "Carolina", "Acosta"},
            new String[]{"30715556665", "Transportes del Oeste S.R.L.", "Gustavo", "Medina"},
            new String[]{"20367778882", "Verdulería La Huerta", "Sergio", "Herrera"},
            new String[]{"27423331114", "Academia de Inglés Smart", "Florencia", "Castro"},
            new String[]{"30704449999", "Electrónica Mendoza S.A.", "Andrés", "Molina"}
    );

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContactoRepository contactoRepository;
    private final DomicilioRepository domicilioRepository;

    public DatosIniciales(
            ClienteRepository clienteRepository,
            UsuarioRepository usuarioRepository,
            ContactoRepository contactoRepository,
            DomicilioRepository domicilioRepository
    ) {
        this.clienteRepository = clienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.contactoRepository = contactoRepository;
        this.domicilioRepository = domicilioRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        completarNombresFaltantes();

        List<String[]> faltantes = CLIENTES_PRUEBA.stream()
                .filter(datos -> clienteRepository.findFirstByCuitCuil(datos[0]).isEmpty())
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
            contacto.setEmail("cliente" + indice + "@ejemplo.com");
            contacto.setCelular("261555000" + indice);
            contacto = contactoRepository.save(contacto);

            Domicilio domicilio = new Domicilio();
            domicilio.setNombreCalle("San Martín");
            domicilio.setNumeroCalle(String.valueOf(100 * indice));
            domicilio = domicilioRepository.save(domicilio);

            Cliente cliente = new Cliente();
            cliente.setCuitCuil(datos[0]);
            cliente.setDenominacion(datos[1]);
            cliente.setNombre(datos[2]);
            cliente.setApellido(datos[3]);
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

    /** Los clientes de prueba cargados antes de existir nombre/apellido se completan una sola vez. */
    private void completarNombresFaltantes() {
        for (String[] datos : CLIENTES_PRUEBA) {
            clienteRepository.findFirstByCuitCuil(datos[0])
                    .filter(cliente -> cliente.getNombre() == null || cliente.getNombre().isBlank())
                    .ifPresent(cliente -> {
                        cliente.setNombre(datos[2]);
                        cliente.setApellido(datos[3]);
                        clienteRepository.save(cliente);
                    });
        }
    }
}

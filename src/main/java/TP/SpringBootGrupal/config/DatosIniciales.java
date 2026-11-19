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
 * Carga clientes de prueba al arrancar, solo si la tabla de clientes está vacía.
 * Así el buscador de clientes siempre tiene datos para probar.
 */
@Component
public class DatosIniciales implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);

    /** CUIT/CUIL de 11 dígitos y razón social de cada cliente de prueba. */
    public static final List<String[]> CLIENTES_PRUEBA = List.of(
            new String[]{"20123456786", "Librería Centro"},
            new String[]{"27234567891", "Escuela Norte"},
            new String[]{"30712345674", "Distribuidora del Sur S.A."},
            new String[]{"20345678905", "Kiosco La Esquina"},
            new String[]{"33698765439", "Papelera Mendoza S.R.L."}
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
        if (clienteRepository.count() > 0) {
            return;
        }

        List<Usuario> usuarios = usuarioRepository.findAll();
        if (usuarios.isEmpty()) {
            log.warn("No hay usuarios cargados: no se pueden crear los clientes de prueba");
            return;
        }

        Usuario usuario = usuarios.get(0);
        Date ahora = new Date();
        int indice = 1;

        for (String[] datos : CLIENTES_PRUEBA) {
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
            cliente.setContacto(contacto);
            cliente.setDomicilio(domicilio);
            cliente.setFechaAlta(ahora);
            cliente.setFechaModificacion(ahora);
            cliente.setUsuarioCarga(usuario);
            cliente.setUsuarioModificacion(usuario);
            clienteRepository.save(cliente);
            indice++;
        }

        log.info("Se cargaron {} clientes de prueba", CLIENTES_PRUEBA.size());
    }
}

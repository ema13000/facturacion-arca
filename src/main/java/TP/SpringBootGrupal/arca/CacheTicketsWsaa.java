package TP.SpringBootGrupal.arca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

final class CacheTicketsWsaa {

    private static final Logger LOG = LoggerFactory.getLogger(CacheTicketsWsaa.class);
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    record Ticket(CredencialesArca credenciales, Instant vencimiento) {
        boolean vigente(Clock reloj) {
            return vencimiento.isAfter(reloj.instant());
        }
    }

    @FunctionalInterface
    interface Solicitud {
        Ticket ejecutar() throws Exception;
    }

    private final Path directorio;
    private final Clock reloj;
    private final Map<String, Ticket> tickets = new HashMap<>();

    CacheTicketsWsaa(Path directorio, Clock reloj) {
        this.directorio = directorio;
        this.reloj = reloj;
    }

    synchronized CredencialesArca obtener(String huellaCertificado, Solicitud solicitar) throws Exception {
        Ticket ticket = tickets.get(huellaCertificado);
        if (ticket != null && ticket.vigente(reloj)) {
            return ticket.credenciales();
        }

        Path archivo = directorio.resolve(huellaCertificado + ".properties");
        ticket = leer(archivo);
        if (ticket == null || !ticket.vigente(reloj)) {
            ticket = solicitar.ejecutar();
            if (!ticket.vigente(reloj)) {
                throw new IllegalStateException("WSAA devolvió un ticket de acceso vencido");
            }
            guardar(archivo, ticket);
        }
        tickets.put(huellaCertificado, ticket);
        return ticket.credenciales();
    }

    private Ticket leer(Path archivo) {
        if (!Files.isRegularFile(archivo, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try (var entrada = Files.newInputStream(archivo)) {
            var datos = new Properties();
            datos.load(entrada);
            String token = datos.getProperty("token", "");
            String firma = datos.getProperty("sign", "");
            if (token.isBlank() || firma.isBlank()) {
                return null;
            }
            return new Ticket(new CredencialesArca(token, firma),
                    Instant.parse(datos.getProperty("vencimiento", "")));
        } catch (IOException | IllegalArgumentException | DateTimeParseException error) {
            LOG.warn("No se pudo recuperar el ticket local de WSAA");
            return null;
        }
    }

    private void guardar(Path archivo, Ticket ticket) {
        Path temporal = null;
        try {
            if (POSIX) {
                Files.createDirectories(directorio, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                temporal = Files.createTempFile(directorio, "ticket-", ".tmp",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } else {
                Files.createDirectories(directorio);
                temporal = Files.createTempFile(directorio, "ticket-", ".tmp");
            }
            var datos = new Properties();
            datos.setProperty("token", ticket.credenciales().token());
            datos.setProperty("sign", ticket.credenciales().sign());
            datos.setProperty("vencimiento", ticket.vencimiento().toString());
            try (var salida = Files.newOutputStream(temporal)) {
                datos.store(salida, null);
            }
            try {
                Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            LOG.warn("No se pudo guardar el ticket de WSAA para reutilizarlo después de reiniciar");
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException error) {
                    LOG.warn("No se pudo limpiar un archivo temporal del ticket local de WSAA");
                }
            }
        }
    }
}

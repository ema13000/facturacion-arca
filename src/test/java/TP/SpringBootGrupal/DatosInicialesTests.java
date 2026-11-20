package TP.SpringBootGrupal;

import TP.SpringBootGrupal.config.DatosIniciales;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DatosInicialesTests {
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ContactoRepository contactos = mock(ContactoRepository.class);
    private final DomicilioRepository domicilios = mock(DomicilioRepository.class);
    private final CondicionIvaRepository condiciones = mock(CondicionIvaRepository.class);

    @BeforeEach
    void preparar() {
        when(usuarios.findAll()).thenReturn(List.of(new Usuario()));
        when(contactos.save(any(Contacto.class))).thenAnswer(i -> i.getArgument(0));
        when(domicilios.save(any(Domicilio.class))).thenAnswer(i -> i.getArgument(0));
        when(clientes.findFirstByCuit(anyString())).thenReturn(Optional.empty());
        for (int codigo : new int[]{1, 4, 5, 6}) {
            CondicionIva condicion = new CondicionIva();
            condicion.setCodigoAfip(codigo);
            condicion.setDenominacion("Condicion " + codigo);
            when(condiciones.findFirstByCodigoAfip(codigo)).thenReturn(Optional.of(condicion));
        }
    }

    private DatosIniciales cargador() {
        return new DatosIniciales(clientes, usuarios, contactos, domicilios, condiciones);
    }

    private List<Cliente> clientesGuardados(int veces) {
        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clientes, times(veces)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void conLaBaseVaciaCargaVeinticincoClientesConElCuitDeSuCondicion() {
        cargador().run();

        List<Cliente> guardados = clientesGuardados(25);
        var correos = new HashSet<String>();
        var cuitsPorCondicion = Map.of(1, "27381112220", 5, "20231813285", 6, "20417364774");
        for (Cliente cliente : guardados) {
            assertTrue(cuitValido(cliente.getCuit()));
            int codigo = cliente.getCondicionIva().getCodigoAfip();
            assertEquals(cuitsPorCondicion.get(codigo), cliente.getCuit(), cliente.getDenominacion());
            assertFalse(cliente.getDenominacion().isBlank());
            assertFalse(cliente.getNombre().isBlank());
            assertFalse(cliente.getApellido().isBlank());
            assertTrue(correos.add(cliente.getContacto().getEmail()));
            assertNotNull(cliente.getDomicilio());
        }
        assertEquals(14, guardados.stream().filter(c -> c.getCondicionIva().getCodigoAfip() == 1).count());
    }

    @Test
    void siYaExistenLosCincoPrimerosSoloAgregaLosQueFaltan() {
        for (String[] datos : DatosIniciales.CLIENTES_PRUEBA.subList(0, 5)) {
            Cliente existente = new Cliente();
            existente.setCuit(datos[0]);
            existente.setNombre(datos[2]);
            existente.setCondicionIva(new CondicionIva());
            when(clientes.findFirstByDenominacionAndCuit(datos[1], datos[0])).thenReturn(Optional.of(existente));
        }

        cargador().run();

        List<Cliente> guardados = clientesGuardados(20);
        assertTrue(guardados.stream().noneMatch(c -> c.getCuit().equals("20123456786")));
    }

    @Test
    void idsDePruebaDistintosNoSeDuplicanAunqueCompartanElCuit() {
        for (int indice : new int[]{3, 21}) {
            String[] datos = DatosIniciales.CLIENTES_PRUEBA.get(indice - 1);
            var existente = new Cliente();
            existente.setDenominacion(datos[1]);
            existente.setNombre(datos[2]);
            existente.setApellido(datos[3]);
            existente.setCuit("27381112220");
            existente.setCondicionIva(condiciones.findFirstByCodigoAfip(1).orElseThrow());
            when(clientes.findFirstByDenominacionAndContacto_Email(datos[1], "cliente" + indice + "@ejemplo.com"))
                    .thenReturn(Optional.of(existente));
        }

        cargador().run();

        var guardados = clientesGuardados(23);
        assertTrue(guardados.stream().noneMatch(c -> List.of("Distribuidora del Sur S.A.", "Sofía Indovino").contains(c.getDenominacion())));
    }

    @Test
    void todosLosClientesTienenCuitConDigitoVerificadorValido() {
        for (String[] datos : DatosIniciales.CLIENTES_PRUEBA) {
            assertTrue(cuitValido(datos[0]), "CUIT inválido: " + datos[0]);
        }
    }

    @Test
    void lasCincoPersonasTienenSuCategoriaFija() {
        cargador().run();

        List<Cliente> guardados = clientesGuardados(25);
        assertEquals(1, categoria(guardados, "Sofía", "Indovino"));
        assertEquals(5, categoria(guardados, "Juan Cruz", "Grivel"));
        assertEquals(6, categoria(guardados, "Emanuel", "Mateo"));
        assertEquals(1, categoria(guardados, "Enzo", "Mellimaci"));
        assertEquals(1, categoria(guardados, "Juan Sebastián", "Cabrera"));
    }

    @Test
    void aLosClientesYaCargadosSinCategoriaSeLesAsignaUna() {
        Cliente existente = new Cliente();
        existente.setCuit("20123456786");
        existente.setNombre("Juan");
        when(clientes.findFirstByDenominacionAndCuit("Librería Centro", "20123456786")).thenReturn(Optional.of(existente));

        cargador().run();

        assertNotNull(existente.getCondicionIva());
        verify(clientes, atLeastOnce()).save(existente);
    }

    private int categoria(List<Cliente> guardados, String nombre, String apellido) {
        return guardados.stream()
                .filter(c -> nombre.equals(c.getNombre()) && apellido.equals(c.getApellido()))
                .findFirst().orElseThrow(() -> new AssertionError("Falta " + nombre + " " + apellido))
                .getCondicionIva().getCodigoAfip();
    }

    private boolean cuitValido(String cuit) {
        int[] pesos = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int suma = 0;
        for (int i = 0; i < 10; i++) {
            suma += Character.getNumericValue(cuit.charAt(i)) * pesos[i];
        }
        int digito = 11 - suma % 11;
        if (digito == 11) digito = 0;
        return digito == Character.getNumericValue(cuit.charAt(10));
    }
}

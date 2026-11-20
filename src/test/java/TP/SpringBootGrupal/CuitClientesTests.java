package TP.SpringBootGrupal;

import TP.SpringBootGrupal.config.DatosIniciales;
import TP.SpringBootGrupal.entities.Cliente;
import TP.SpringBootGrupal.entities.CondicionIva;
import TP.SpringBootGrupal.entities.Usuario;
import TP.SpringBootGrupal.repository.*;
import TP.SpringBootGrupal.service.ReglasCuit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CuitClientesTests {

    // ---------- Reglas del CUIT ----------

    @ParameterizedTest
    @CsvSource({
            "20123456786, true", "27381112220, true", "30712345671, true",
            "30712345674, false", "20345678905, false", "33698765439, false",
            "2012345678, false", "20-12345678-6, false", "abcdefghijk, false"
    })
    void elDigitoVerificadorTieneQueCoincidir(String cuit, boolean valido) {
        assertEquals(valido, ReglasCuit.esValido(cuit));
    }

    @ParameterizedTest
    @CsvSource({
            // CUIT, código ARCA de la condición, ¿coherente?
            "20123456786, 6, true",   // persona monotributista
            "27381112220, 1, true",   // persona inscripta
            "20352223337, 5, true",   // persona consumidor final
            "30712345671, 1, true",   // empresa inscripta
            "30712345671, 4, true",   // empresa exenta
            "30712345671, 6, false",  // una empresa no puede ser monotributista
            "33698765432, 5, false"   // ni consumidor final
    })
    void elTipoDeCuitTieneQueCoincidirConLaCondicion(String cuit, int condicion, boolean coherente) {
        assertEquals(coherente, ReglasCuit.coincideConCondicion(cuit, condicion));
    }

    @Test
    void todosLosClientesDePruebaTienenCuitValidoYCoherenteConSuCategoria() {
        for (String[] datos : DatosIniciales.CLIENTES_PRUEBA) {
            assertTrue(ReglasCuit.esValido(datos[0]), "CUIT inválido de " + datos[1] + ": " + datos[0]);
            assertTrue(ReglasCuit.coincideConCondicion(datos[0], Integer.parseInt(datos[4])),
                    "CUIT incoherente con la categoría de " + datos[1] + ": " + datos[0]);
        }
    }

    @Test
    void cadaCuitCorregidoReemplazaAUnoInvalidoYEsValido() {
        assertEquals(Map.of(
                "30712345674", "30712345671",
                "20345678905", "20345678906",
                "33698765439", "33698765432"
        ), DatosIniciales.CUITS_CORREGIDOS);
    }

    // ---------- Corrección de los clientes ya cargados ----------

    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CondicionIvaRepository condiciones = mock(CondicionIvaRepository.class);

    @BeforeEach
    void preparar() {
        when(usuarios.findAll()).thenReturn(List.of(new Usuario()));
        when(condiciones.findFirstByCodigoAfip(org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Optional.of(new CondicionIva()));
        // Todos los clientes de prueba ya existen, con nombre y categoría
        when(clientes.findFirstByCuit(anyString())).thenAnswer(i -> Optional.of(cliente(i.getArgument(0))));
        when(clientes.findFirstByDenominacionAndCuit(anyString(), anyString()))
                .thenAnswer(i -> Optional.of(cliente(i.getArgument(1))));
    }

    private Cliente cliente(String cuit) {
        Cliente cliente = new Cliente();
        cliente.setCuit(cuit);
        cliente.setNombre("Ya cargado");
        cliente.setCondicionIva(new CondicionIva());
        return cliente;
    }

    private void correr() {
        new DatosIniciales(clientes, usuarios, mock(ContactoRepository.class),
                mock(DomicilioRepository.class), condiciones).run();
    }

    @Test
    void unClienteConElCuitViejoPasaAlCorregidoSinCrearOtro() {
        Cliente distribuidora = cliente("30712345674");
        when(clientes.findFirstByCuit("30712345674")).thenReturn(Optional.of(distribuidora));
        when(clientes.findFirstByCuit("30712345671")).thenReturn(Optional.empty());
        when(clientes.findFirstByDenominacionAndCuit("Distribuidora del Sur S.A.", "30712345671"))
                .thenReturn(Optional.of(distribuidora));

        correr();

        assertEquals("30712345671", distribuidora.getCuit());
        verify(clientes).save(distribuidora);
        verify(clientes, times(1)).save(any(Cliente.class));
    }

    @Test
    void siElCuitCorregidoYaExisteNoSeTocaNada() {
        correr();

        verify(clientes, never()).save(any(Cliente.class));
    }
}

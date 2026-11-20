package TP.SpringBootGrupal;

import TP.SpringBootGrupal.config.CondicionesIniciales;
import TP.SpringBootGrupal.entities.CondicionIva;
import TP.SpringBootGrupal.entities.PuntoVenta;
import TP.SpringBootGrupal.entities.Usuario;
import TP.SpringBootGrupal.repository.CondicionIvaRepository;
import TP.SpringBootGrupal.repository.PuntoVentaRepository;
import TP.SpringBootGrupal.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class CondicionesInicialesTests {
    private final CondicionIvaRepository condiciones = mock(CondicionIvaRepository.class);
    private final PuntoVentaRepository puntos = mock(PuntoVentaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);

    private final PuntoVenta central = new PuntoVenta();

    @BeforeEach
    void preparar() {
        when(usuarios.findAll()).thenReturn(List.of(new Usuario()));
        when(condiciones.findFirstByCodigoAfip(anyInt())).thenReturn(Optional.empty());
        CondicionIva consumidorFinal = new CondicionIva();
        consumidorFinal.setCodigoAfip(5);
        when(condiciones.findFirstByCodigoAfip(5)).thenReturn(Optional.of(consumidorFinal));
        central.setNumero(1);
        central.setDescripcion("Sucursal Central");
        when(puntos.findAll()).thenReturn(List.of(central));
    }

    private void correr() {
        new CondicionesIniciales(condiciones, puntos, usuarios).run();
    }

    @Test
    void creaLasCondicionesQueFaltanSinDuplicarConsumidorFinal() {
        correr();

        ArgumentCaptor<CondicionIva> captor = ArgumentCaptor.forClass(CondicionIva.class);
        verify(condiciones, times(3)).save(captor.capture());
        assertEquals(List.of(1, 4, 6), captor.getAllValues().stream().map(CondicionIva::getCodigoAfip).sorted().toList());
        assertTrue(captor.getAllValues().stream().allMatch(c -> c.getUsuarioCarga() != null));
    }

    @Test
    void elPuntoDeVentaExistenteQuedaMonotributistaYSeCreaUnoInscripto() {
        correr();

        assertEquals(Boolean.FALSE, central.getResponsableInscripto());
        ArgumentCaptor<PuntoVenta> captor = ArgumentCaptor.forClass(PuntoVenta.class);
        verify(puntos, atLeastOnce()).save(captor.capture());
        PuntoVenta inscripto = captor.getAllValues().stream()
                .filter(p -> Boolean.TRUE.equals(p.getResponsableInscripto()))
                .findFirst().orElseThrow();
        assertEquals(2, inscripto.getNumero());
    }

    @Test
    void siYaHayUnPuntoInscriptoNoCreaOtro() {
        central.setResponsableInscripto(false);
        PuntoVenta yaInscripto = new PuntoVenta();
        yaInscripto.setNumero(2);
        yaInscripto.setResponsableInscripto(true);
        when(puntos.findAll()).thenReturn(List.of(central, yaInscripto));

        correr();

        verify(puntos, never()).save(any(PuntoVenta.class));
    }
}

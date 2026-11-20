package TP.SpringBootGrupal;

import TP.SpringBootGrupal.config.CondicionesIniciales;
import TP.SpringBootGrupal.entities.CondicionIva;
import TP.SpringBootGrupal.entities.PuntoVenta;
import TP.SpringBootGrupal.entities.TipoMoneda;
import TP.SpringBootGrupal.entities.Usuario;
import TP.SpringBootGrupal.repository.CondicionIvaRepository;
import TP.SpringBootGrupal.repository.PuntoVentaRepository;
import TP.SpringBootGrupal.repository.TipoMonedaRepository;
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
    private final TipoMonedaRepository monedas = mock(TipoMonedaRepository.class);
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
        new CondicionesIniciales(condiciones, puntos, monedas, usuarios).run();
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
    void completaTresPuntosMonotributistasReutilizandoElEjemploAnterior() {
        central.setDescripcion("Sucursal Responsable Inscripto");
        central.setResponsableInscripto(true);
        correr();

        assertEquals(Boolean.FALSE, central.getResponsableInscripto());
        ArgumentCaptor<PuntoVenta> captor = ArgumentCaptor.forClass(PuntoVenta.class);
        verify(puntos, times(3)).save(captor.capture());
        assertEquals(List.of(1, 2, 3), captor.getAllValues().stream().map(PuntoVenta::getNumero).toList());
        assertTrue(captor.getAllValues().stream().allMatch(p -> Boolean.FALSE.equals(p.getResponsableInscripto())));
        assertEquals("Sucursal Los Álamos", central.getDescripcion());
    }

    @Test
    void conservaElPuntoInscriptoConfiguradoYAgregaSoloLosMonotributistasFaltantes() {
        central.setResponsableInscripto(false);
        PuntoVenta yaInscripto = new PuntoVenta();
        yaInscripto.setNumero(2);
        yaInscripto.setResponsableInscripto(true);
        when(puntos.findAll()).thenReturn(List.of(central, yaInscripto));

        correr();

        ArgumentCaptor<PuntoVenta> captor = ArgumentCaptor.forClass(PuntoVenta.class);
        verify(puntos, times(2)).save(captor.capture());
        assertEquals(List.of(3, 4), captor.getAllValues().stream().map(PuntoVenta::getNumero).toList());
        assertEquals(Boolean.TRUE, yaInscripto.getResponsableInscripto());
    }

    @Test
    void creaLosTresPuntosYElPesoEnUnaBaseSinCatalogos() {
        when(puntos.findAll()).thenReturn(List.of());

        correr();

        ArgumentCaptor<PuntoVenta> puntosNuevos = ArgumentCaptor.forClass(PuntoVenta.class);
        verify(puntos, times(3)).save(puntosNuevos.capture());
        assertEquals(List.of(1, 2, 3), puntosNuevos.getAllValues().stream().map(PuntoVenta::getNumero).toList());
        ArgumentCaptor<TipoMoneda> monedasNuevas = ArgumentCaptor.forClass(TipoMoneda.class);
        verify(monedas).save(monedasNuevas.capture());
        assertEquals("PES", monedasNuevas.getValue().getCodigoAfip());
        assertEquals(List.of("Sucursal Los Álamos", "Sucursal La Arboleda", "Sucursal Los Tilos"),
                puntosNuevos.getAllValues().stream().map(PuntoVenta::getDescripcion).toList());
        assertTrue(monedasNuevas.getAllValues().stream().allMatch(m -> m.getUsuarioCarga() != null));
    }

    @Test
    void renombraElPuntoDeEjemploNumeradoSinCambiarSuNumero() {
        central.setDescripcion("Sucursal 1");
        central.setTipoEmision("ELECTRONICA");
        central.setResponsableInscripto(false);

        correr();

        assertEquals(1, central.getNumero());
        assertEquals("Sucursal Los Álamos", central.getDescripcion());
        verify(puntos).save(central);
    }

    @Test
    void noDuplicaPuntosNiMonedasSiYaEstanCargados() {
        central.setResponsableInscripto(false);
        PuntoVenta sucursalDos = new PuntoVenta();
        sucursalDos.setResponsableInscripto(false);
        PuntoVenta sucursalTres = new PuntoVenta();
        sucursalTres.setResponsableInscripto(false);
        when(puntos.findAll()).thenReturn(List.of(central, sucursalDos, sucursalTres));
        TipoMoneda pesos = new TipoMoneda();
        pesos.setCodigoAfip("PES");
        TipoMoneda dolares = new TipoMoneda();
        dolares.setCodigoAfip("DOL");
        when(monedas.findAll()).thenReturn(List.of(pesos, dolares));

        correr();

        verify(puntos, never()).save(any(PuntoVenta.class));
        verify(monedas, never()).save(any(TipoMoneda.class));
    }
}

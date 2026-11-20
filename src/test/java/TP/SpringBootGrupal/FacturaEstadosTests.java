package TP.SpringBootGrupal;

import TP.SpringBootGrupal.dtos.Factura.FacturaRequestDTO;
import TP.SpringBootGrupal.dtos.FacturaDetalle.FacturaDetalleRequestDTO;
import TP.SpringBootGrupal.entities.*;
import TP.SpringBootGrupal.repository.*;
import TP.SpringBootGrupal.service.FacturaServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FacturaEstadosTests {
    private final FacturaVentaRepository facturas = mock(FacturaVentaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final FacturaServiceImpl servicio = new FacturaServiceImpl(
            facturas, mock(ClienteRepository.class), mock(CondicionIvaRepository.class),
            mock(TipoMonedaRepository.class), mock(PuntoVentaRepository.class),
            mock(ListaPrecioArticuloRepository.class), usuarios);

    private FacturaVenta factura(long id, String estado) {
        CondicionIva condicion = new CondicionIva();
        condicion.setDenominacion("Consumidor Final");
        PuntoVenta punto = new PuntoVenta();
        punto.setDescripcion("Central");
        FacturaVenta factura = new FacturaVenta();
        factura.setId(id);
        factura.setNumero(id);
        factura.setEstado(estado);
        factura.setCondicionIva(condicion);
        factura.setPuntoVenta(punto);
        factura.setFechaEmision(new Date());
        when(facturas.findById(id)).thenReturn(Optional.of(factura));
        when(usuarios.findById(1L)).thenReturn(Optional.of(new Usuario()));
        when(facturas.save(any(FacturaVenta.class))).thenAnswer(i -> i.getArgument(0));
        return factura;
    }

    private FacturaRequestDTO dto() {
        return new FacturaRequestDTO(null, 1L, 1L, 1L, List.of(new FacturaDetalleRequestDTO(1L, 1.0, 0.0)));
    }

    @Test
    void elListadoMuestraPrimeroLaUltimaFactura() {
        var vieja = factura(1, "PENDIENTE");
        var nueva = factura(3, "AUTORIZADA");
        var media = factura(2, "PENDIENTE");
        when(facturas.findAll()).thenReturn(List.of(vieja, nueva, media));
        assertEquals(List.of(3L, 2L, 1L),
                servicio.findAllParaListado().stream().map(f -> f.getId()).toList());
    }

    @Test
    void elListadoOcultaLasEliminadasPeroMuestraLasAnuladas() {
        var eliminada = factura(1, "PENDIENTE");
        eliminada.setFechaBaja(new Date());
        var anulada = factura(2, "ANULADA");
        anulada.setFechaBaja(new Date());
        when(facturas.findAll()).thenReturn(List.of(eliminada, anulada));
        assertEquals(List.of(2L),
                servicio.findAllParaListado().stream().map(f -> f.getId()).toList());
    }

    @Test
    void unaAutorizadaNoSePuedeEditar() {
        factura(5, "AUTORIZADA");
        var error = assertThrows(IllegalStateException.class, () -> servicio.update(dto(), 5L, 1L, 0));
        assertTrue(error.getMessage().contains("autorizada"));
        verify(facturas, never()).save(any());
    }

    @Test
    void unaAutorizadaNoSePuedeEliminar() {
        factura(5, "AUTORIZADA");
        assertThrows(IllegalStateException.class, () -> servicio.delete(5L, 1L));
        verify(facturas, never()).save(any());
    }

    @Test
    void unaPendienteSePuedeEliminar() {
        var pendiente = factura(6, "PENDIENTE");
        servicio.delete(6L, 1L);
        assertNotNull(pendiente.getFechaBaja());
        assertEquals("PENDIENTE", pendiente.getEstado());
    }

    @Test
    void unaPendienteNoSePuedeAnular() {
        factura(6, "PENDIENTE");
        assertThrows(IllegalStateException.class, () -> servicio.anular(6L, 1L, "error"));
        verify(facturas, never()).save(any());
    }

    @Test
    void unaAutorizadaSePuedeAnular() {
        var autorizada = factura(5, "AUTORIZADA");
        var resultado = servicio.anular(5L, 1L, "devolución");
        assertEquals("ANULADA", resultado.getEstado());
        assertEquals("devolución", autorizada.getMotivoAnulacion());
        assertNotNull(autorizada.getFechaAnulacion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDIENTE", "ANULADA"})
    void soloSeImprimeUnaAutorizada(String estado) {
        factura(7, estado);
        assertThrows(IllegalStateException.class, () -> servicio.findParaImpresion(7L));
    }

    @Test
    void laImpresionIncluyeCaeVencimientoYDetalles() {
        var autorizada = factura(8, "AUTORIZADA");
        Date vencimiento = new Date();
        autorizada.setCae("75123456789012");
        autorizada.setCaeFechaVencimiento(vencimiento);
        autorizada.setImporteTotal(250);
        FacturaVentaDetalle detalle = new FacturaVentaDetalle();
        detalle.setDescripcion("Cuaderno azul");
        detalle.setCantidad(2);
        detalle.setPrecioUnitario(125);
        detalle.setImporteSubtotal(250);
        autorizada.getDetalles().add(detalle);

        var impresion = servicio.findParaImpresion(8L);

        assertEquals("75123456789012", impresion.cae());
        assertEquals(vencimiento, impresion.caeFechaVencimiento());
        assertEquals(250, impresion.importeTotal());
        assertEquals("Cuaderno azul", impresion.detalles().get(0).descripcion());
        assertEquals("Consumidor Final", impresion.clienteDenominacion());
    }
}

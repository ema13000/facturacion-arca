package TP.SpringBootGrupal;

import TP.SpringBootGrupal.config.ArticulosIniciales;
import TP.SpringBootGrupal.dtos.Factura.FacturaEdicionDTO;
import TP.SpringBootGrupal.dtos.Factura.FacturaRequestDTO;
import TP.SpringBootGrupal.dtos.FacturaDetalle.FacturaDetalleRequestDTO;
import TP.SpringBootGrupal.entities.*;
import TP.SpringBootGrupal.repository.*;
import TP.SpringBootGrupal.service.CatalogoService;
import TP.SpringBootGrupal.service.FacturaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class ComprobanteDatosTests {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));

    private final FacturaVentaRepository facturas = mock(FacturaVentaRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final CondicionIvaRepository condiciones = mock(CondicionIvaRepository.class);
    private final TipoMonedaRepository monedas = mock(TipoMonedaRepository.class);
    private final PuntoVentaRepository puntos = mock(PuntoVentaRepository.class);
    private final ListaPrecioArticuloRepository precios = mock(ListaPrecioArticuloRepository.class);
    private final ListaPrecioRepository listas = mock(ListaPrecioRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final FacturaServiceImpl servicio = new FacturaServiceImpl(
            facturas, clientes, condiciones, monedas, puntos, precios, usuarios);

    @BeforeEach
    void preparar() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(new Usuario()));
        when(usuarios.findAll()).thenReturn(List.of(new Usuario()));
        when(monedas.findById(anyLong())).thenReturn(Optional.of(new TipoMoneda()));
        CondicionIva consumidorFinal = new CondicionIva();
        consumidorFinal.setId(1L);
        consumidorFinal.setCodigoAfip(5);
        consumidorFinal.setDenominacion("Consumidor Final");
        when(condiciones.findById(1L)).thenReturn(Optional.of(consumidorFinal));
        PuntoVenta punto = new PuntoVenta();
        punto.setId(1L);
        when(puntos.findById(1L)).thenReturn(Optional.of(punto));
        when(precios.findById(1L)).thenReturn(Optional.of(precio(1L, "ART-001", 1000, lista(1L, "Minorista"))));
        when(facturas.save(any(FacturaVenta.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ListaPrecio lista(long id, String nombre) {
        ListaPrecio lista = new ListaPrecio();
        lista.setId(id);
        lista.setDenominacion(nombre);
        return lista;
    }

    private ListaPrecioArticulo precio(long id, String codigo, double importe, ListaPrecio lista) {
        Articulo articulo = new Articulo();
        articulo.setId(id);
        articulo.setCodigo(codigo);
        articulo.setDenominacion("Artículo " + codigo);
        ListaPrecioArticulo precio = new ListaPrecioArticulo();
        precio.setId(id);
        precio.setArticulo(articulo);
        precio.setListaPrecio(lista);
        precio.setPrecioVenta(importe);
        return precio;
    }

    private FacturaRequestDTO dto(LocalDate fecha, String observaciones) {
        return new FacturaRequestDTO(null, 1L, 1L, 1L,
                List.of(new FacturaDetalleRequestDTO(1L, 1.0, 0.0)), fecha, observaciones);
    }

    private FacturaVenta guardada() {
        ArgumentCaptor<FacturaVenta> captor = ArgumentCaptor.forClass(FacturaVenta.class);
        verify(facturas).save(captor.capture());
        return captor.getValue();
    }

    private LocalDate fechaDe(FacturaVenta factura) {
        return new java.sql.Date(factura.getFechaEmision().getTime()).toLocalDate();
    }

    // ---------- Fecha de emisión ----------

    @ParameterizedTest
    @ValueSource(ints = {-5, -1, 0, 3, 5})
    void guardaLaFechaDeEmisionElegida(int dias) {
        servicio.save(dto(HOY.plusDays(dias), null), 1L, 0);

        assertEquals(HOY.plusDays(dias), fechaDe(guardada()));
    }

    @Test
    void sinFechaSeUsaLaDeHoy() {
        servicio.save(dto(null, null), 1L, 0);

        assertEquals(HOY, fechaDe(guardada()));
    }

    @ParameterizedTest
    @ValueSource(ints = {-6, 6, -30})
    void rechazaFechasQueArcaNoAcepta(int dias) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> servicio.save(dto(HOY.plusDays(dias), null), 1L, 0));

        assertTrue(error.getMessage().contains("5 días"), error.getMessage());
        verify(facturas, never()).save(any());
    }

    // ---------- Observaciones ----------

    @Test
    void guardaLasObservacionesSinEspaciosSobrantes() {
        servicio.save(dto(HOY, "  Entregar por la tarde  "), 1L, 0);

        assertEquals("Entregar por la tarde", guardada().getObservaciones());
    }

    @Test
    void observacionesVaciasQuedanEnNull() {
        servicio.save(dto(HOY, "   "), 1L, 0);

        assertNull(guardada().getObservaciones());
    }

    @Test
    void rechazaObservacionesDeMasDe500Caracteres() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.save(dto(HOY, "x".repeat(501)), 1L, 0));
    }

    @Test
    void laEdicionTraeFechaYObservaciones() {
        FacturaVenta factura = new FacturaVenta();
        factura.setId(4L);
        factura.setNumero(4L);
        factura.setEstado("PENDIENTE");
        factura.setFechaEmision(java.sql.Date.valueOf(HOY.minusDays(2)));
        factura.setObservaciones("Pago contado");
        factura.setCondicionIva(condiciones.findById(1L).orElseThrow());
        factura.setTipoMoneda(new TipoMoneda());
        factura.setPuntoVenta(puntos.findById(1L).orElseThrow());
        when(facturas.findById(4L)).thenReturn(Optional.of(factura));

        FacturaEdicionDTO edicion = servicio.findParaEdicion(4L);

        assertEquals(HOY.minusDays(2), edicion.fechaEmision());
        assertEquals("Pago contado", edicion.observaciones());
    }

    @Test
    void anularConservaLasObservacionesYAgregaElMotivo() {
        FacturaVenta factura = new FacturaVenta();
        factura.setId(5L);
        factura.setNumero(5L);
        factura.setEstado("AUTORIZADA");
        factura.setFechaEmision(new Date());
        factura.setObservaciones("Pago contado");
        factura.setCondicionIva(condiciones.findById(1L).orElseThrow());
        factura.setPuntoVenta(puntos.findById(1L).orElseThrow());
        when(facturas.findById(5L)).thenReturn(Optional.of(factura));

        assertEquals("Pago contado", servicio.findParaImpresion(5L).observaciones());
        var resultado = servicio.anular(5L, 1L, " Error en el importe ");

        assertEquals("Pago contado", factura.getObservaciones());
        assertEquals("Error en el importe", resultado.getMotivoAnulacion());
    }

    @Test
    void sePuedeLeerElMotivoDeLasFacturasAnuladasAntesDelCambio() {
        var factura = new FacturaVenta();
        factura.setEstado("ANULADA");
        factura.setPuntoVenta(puntos.findById(1L).orElseThrow());
        factura.setCondicionIva(condiciones.findById(1L).orElseThrow());
        factura.setObservaciones("Pago contado | Anulada: Error en el importe");
        assertEquals("Error en el importe",
                TP.SpringBootGrupal.dtos.Factura.FacturaReporteDTO.toDTO(factura).getMotivoAnulacion());

        factura.setObservaciones("Anulada: sin motivo");
        assertNull(TP.SpringBootGrupal.dtos.Factura.FacturaReporteDTO.toDTO(factura).getMotivoAnulacion());
    }

    // ---------- Listas Minorista y Mayorista ----------

    private ArticulosIniciales cargador() {
        ArticuloRepository articulos = mock(ArticuloRepository.class);
        when(articulos.count()).thenReturn((long) ArticulosIniciales.CANTIDAD_ARTICULOS);
        return new ArticulosIniciales(articulos, listas, precios, usuarios);
    }

    @Test
    void laListaGeneralPasaASerMinoristaYSeCreaMayoristaUn15PorCientoMasBarata() {
        ListaPrecio general = lista(1L, "Lista general");
        when(listas.findByFechaBajaIsNullOrderByDenominacion()).thenReturn(List.of(general));
        when(listas.save(any(ListaPrecio.class))).thenAnswer(i -> {
            ListaPrecio guardada = i.getArgument(0);
            if (guardada.getId() == null) guardada.setId(2L);
            return guardada;
        });
        when(precios.findByListaPrecio_Id(1L)).thenReturn(List.of(
                precio(10L, "ART-001", 1000, general), precio(11L, "ART-002", 2000, general)));

        cargador().run();

        assertEquals("Minorista", general.getDenominacion());
        ArgumentCaptor<ListaPrecioArticulo> captor = ArgumentCaptor.forClass(ListaPrecioArticulo.class);
        verify(precios, times(2)).save(captor.capture());
        assertEquals(List.of(850.0, 1700.0), captor.getAllValues().stream().map(ListaPrecioArticulo::getPrecioVenta).toList());
        assertTrue(captor.getAllValues().stream().allMatch(p -> "Mayorista".equals(p.getListaPrecio().getDenominacion())));
    }

    @Test
    void siYaExistenMinoristaYMayoristaNoDuplicaPrecios() {
        ListaPrecio minorista = lista(1L, "Minorista");
        ListaPrecio mayorista = lista(2L, "Mayorista");
        when(listas.findByFechaBajaIsNullOrderByDenominacion()).thenReturn(List.of(mayorista, minorista));
        when(precios.findByListaPrecio_Id(1L)).thenReturn(List.of(precio(10L, "ART-001", 1000, minorista)));
        when(precios.existsByListaPrecio_IdAndArticulo_Id(2L, 10L)).thenReturn(true);

        cargador().run();

        verify(precios, never()).save(any());
        verify(listas, never()).save(any());
    }

    // ---------- Búsqueda de artículos paginada ----------

    private CatalogoService catalogo() {
        return new CatalogoService(puntos, monedas, condiciones, clientes, precios, listas);
    }

    @Test
    void buscarSinFiltroTraeTodasLasListasDeA20PorPagina() {
        var minorista = lista(1L, "Minorista");
        var contenido = List.of(precio(1L, "ART-001", 1000, minorista), precio(2L, "ART-002", 2000, minorista));
        when(precios.buscarPagina(isNull(), eq(""), eq(PageRequest.of(1, 20))))
                .thenReturn(new PageImpl<>(contenido, PageRequest.of(1, 20), 200));

        var pagina = catalogo().buscarPreciosArticulos(null, "  ", 1);

        assertEquals(List.of(1L, 2L), pagina.contenido().stream().map(p -> p.id()).toList());
        assertEquals(1, pagina.pagina());
        assertEquals(10, pagina.totalPaginas());
        assertEquals(200, pagina.totalElementos());
    }

    @Test
    void buscarFiltraPorListaYTexto() {
        var mayorista = lista(2L, "Mayorista");
        when(precios.buscarPagina(eq(2L), eq("cuad"), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(precio(5L, "ART-005", 850, mayorista)), PageRequest.of(0, 20), 1));

        var pagina = catalogo().buscarPreciosArticulos(2L, "cuad", 0);

        assertEquals("Mayorista", pagina.contenido().get(0).listaPrecioDenominacion());
        assertEquals(1, pagina.totalPaginas());
    }

    @Test
    void unaPaginaNegativaSeTomaComoLaPrimera() {
        when(precios.buscarPagina(isNull(), eq(""), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var pagina = catalogo().buscarPreciosArticulos(null, null, -3);

        assertEquals(0, pagina.pagina());
        assertTrue(pagina.contenido().isEmpty());
    }
}

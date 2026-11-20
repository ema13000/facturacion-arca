package TP.SpringBootGrupal;

import TP.SpringBootGrupal.arca.AfipWsaaClient;
import TP.SpringBootGrupal.arca.AfipWsfeClient;
import TP.SpringBootGrupal.dtos.Factura.FacturaRequestDTO;
import TP.SpringBootGrupal.dtos.FacturaDetalle.FacturaDetalleRequestDTO;
import TP.SpringBootGrupal.entities.*;
import TP.SpringBootGrupal.repository.*;
import TP.SpringBootGrupal.service.FacturaServiceImpl;
import TP.SpringBootGrupal.service.FacturacionArcaService;
import TP.SpringBootGrupal.service.TipoComprobante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class FacturaTiposTests {

    // ---------- Regla de la letra ----------

    @ParameterizedTest
    @CsvSource({
            // emisor inscripto, código AFIP del receptor, letra esperada
            "false, 1, C", "false, 5, C", "false, 6, C",
            "true, 1, A", "true, 6, A",
            "true, 5, B", "true, 4, B"
    })
    void laLetraDependeDelEmisorYDelReceptor(boolean emisorInscripto, int receptor, String letra) {
        assertEquals(letra, TipoComprobante.letra(emisorInscripto, receptor));
    }

    @ParameterizedTest
    @CsvSource({"A, 1", "B, 6", "C, 11"})
    void cadaLetraTieneSuCodigoDeArca(String letra, int codigo) {
        assertEquals(codigo, TipoComprobante.codigoArca(letra));
    }

    // ---------- Guardado de facturas ----------

    private final FacturaVentaRepository facturas = mock(FacturaVentaRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final CondicionIvaRepository condiciones = mock(CondicionIvaRepository.class);
    private final TipoMonedaRepository monedas = mock(TipoMonedaRepository.class);
    private final PuntoVentaRepository puntos = mock(PuntoVentaRepository.class);
    private final ListaPrecioArticuloRepository precios = mock(ListaPrecioArticuloRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final FacturaServiceImpl servicio = new FacturaServiceImpl(
            facturas, clientes, condiciones, monedas, puntos, precios, usuarios);

    private final CondicionIva consumidorFinal = condicion(10L, 5, "Consumidor Final");
    private final CondicionIva inscripto = condicion(11L, 1, "IVA Responsable Inscripto");
    private final CondicionIva monotributo = condicion(12L, 6, "Responsable Monotributo");

    private CondicionIva condicion(long id, int codigo, String nombre) {
        CondicionIva condicion = new CondicionIva();
        condicion.setId(id);
        condicion.setCodigoAfip(codigo);
        condicion.setDenominacion(nombre);
        return condicion;
    }

    @BeforeEach
    void preparar() {
        when(usuarios.findById(1L)).thenReturn(Optional.of(new Usuario()));
        when(monedas.findById(anyLong())).thenReturn(Optional.of(new TipoMoneda()));
        for (CondicionIva c : List.of(consumidorFinal, inscripto, monotributo)) {
            when(condiciones.findById(c.getId())).thenReturn(Optional.of(c));
        }
        when(condiciones.findFirstByCodigoAfip(5)).thenReturn(Optional.of(consumidorFinal));
        when(facturas.save(any(FacturaVenta.class))).thenAnswer(i -> i.getArgument(0));

        Articulo articulo = new Articulo();
        articulo.setDenominacion("Resma A4");
        ListaPrecio lista = new ListaPrecio();
        ListaPrecioArticulo precio = new ListaPrecioArticulo();
        precio.setId(1L);
        precio.setArticulo(articulo);
        precio.setListaPrecio(lista);
        precio.setPrecioVenta(1210);
        when(precios.findById(1L)).thenReturn(Optional.of(precio));
    }

    private void puntoDeVenta(boolean emisorInscripto) {
        PuntoVenta punto = new PuntoVenta();
        punto.setId(1L);
        punto.setNumero(1);
        punto.setResponsableInscripto(emisorInscripto);
        when(puntos.findById(1L)).thenReturn(Optional.of(punto));
    }

    private Cliente cliente(long id, CondicionIva condicion) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setDenominacion("Cliente " + id);
        cliente.setCondicionIva(condicion);
        when(clientes.findById(id)).thenReturn(Optional.of(cliente));
        return cliente;
    }

    private FacturaVenta guardar(Long clienteId, Long condicionPedida) {
        servicio.save(new FacturaRequestDTO(clienteId, condicionPedida, 1L, 1L,
                List.of(new FacturaDetalleRequestDTO(1L, 1.0, 0.0))), 1L, 0);
        ArgumentCaptor<FacturaVenta> captor = ArgumentCaptor.forClass(FacturaVenta.class);
        verify(facturas).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void emisorInscriptoAClienteInscriptoEsFacturaAConIvaDiscriminado() {
        puntoDeVenta(true);
        cliente(7L, inscripto);

        FacturaVenta factura = guardar(7L, inscripto.getId());

        assertEquals("A", factura.getTipoComprobante());
        assertEquals(1210, factura.getImporteTotal(), 0.001);
        assertEquals(1000, factura.getDetalles().get(0).getImporteNeto(), 0.001);
        assertEquals(210, factura.getDetalles().get(0).getImporteIva(), 0.001);
    }

    @Test
    void emisorInscriptoSinClienteEsFacturaBAConsumidorFinal() {
        puntoDeVenta(true);

        FacturaVenta factura = guardar(null, inscripto.getId());

        assertEquals("B", factura.getTipoComprobante());
        assertEquals("Consumidor Final", factura.getCondicionIva().getDenominacion());
        assertEquals(1210, factura.getImporteTotal(), 0.001);
        assertEquals(210, factura.getDetalles().get(0).getImporteIva(), 0.001);
    }

    @Test
    void emisorMonotributistaSiempreEsFacturaCSinIva() {
        puntoDeVenta(false);
        cliente(8L, inscripto);

        FacturaVenta factura = guardar(8L, inscripto.getId());

        assertEquals("C", factura.getTipoComprobante());
        assertEquals(1210, factura.getImporteTotal(), 0.001);
        assertEquals(0, factura.getDetalles().get(0).getImporteIva(), 0.001);
    }

    @Test
    void laCondicionDelClienteNoSePuedeCambiarDesdeElFormulario() {
        puntoDeVenta(false);
        cliente(9L, monotributo);

        FacturaVenta factura = guardar(9L, consumidorFinal.getId());

        assertEquals("Responsable Monotributo", factura.getCondicionIva().getDenominacion());
    }

    @Test
    void sinClienteSiempreQuedaConsumidorFinalAunqueSePidaOtra() {
        puntoDeVenta(false);

        FacturaVenta factura = guardar(null, monotributo.getId());

        assertEquals("Consumidor Final", factura.getCondicionIva().getDenominacion());
    }

    // ---------- Autorización en ARCA ----------

    @ParameterizedTest
    @CsvSource({"A", "B"})
    void arcaRechazaFacturasAyBSinTocarElServicio(String letra) {
        AfipWsaaClient wsaa = mock(AfipWsaaClient.class);
        AfipWsfeClient wsfe = mock(AfipWsfeClient.class);
        FacturacionArcaService arca = new FacturacionArcaService(wsaa, wsfe, facturas);
        FacturaVenta factura = new FacturaVenta();
        factura.setEstado("PENDIENTE");
        factura.setTipoComprobante(letra);

        var error = assertThrows(IllegalStateException.class, () -> arca.autorizarFacturaC(factura, 99, 0));

        assertTrue(error.getMessage().contains("Factura " + letra), error.getMessage());
        assertTrue(error.getMessage().contains("Responsable Inscripto"), error.getMessage());
        verifyNoInteractions(wsaa, wsfe);
    }

    @Test
    void elControladorDeArcaDevuelveElMotivoConCodigo409() {
        var controlador = new TP.SpringBootGrupal.controller.ArcaRestController(mock(FacturacionArcaService.class));

        var respuesta = controlador.operacionNoPermitida(new IllegalStateException("Para autorizar la Factura A ..."));

        assertEquals(409, respuesta.getStatusCode().value());
        assertEquals("Para autorizar la Factura A ...", respuesta.getBody().get("mensaje"));
    }

    // ---------- Datos que ve la pantalla ----------

    private TP.SpringBootGrupal.service.CatalogoService catalogo() {
        return new TP.SpringBootGrupal.service.CatalogoService(puntos, monedas, condiciones, clientes,
                precios, mock(ListaPrecioRepository.class));
    }

    @Test
    void elClienteBuscadoTraeSuCategoriaDeIva() {
        Cliente emanuel = cliente(20L, monotributo);
        emanuel.setCuitCuil("20334447775");

        var dto = catalogo().obtenerCliente(20L);

        assertEquals(12L, dto.condicionIvaId());
        assertEquals("Responsable Monotributo", dto.condicionIva());
        assertEquals(6, dto.condicionIvaCodigo());
    }

    @Test
    void unClienteSinCategoriaNoInventaUna() {
        cliente(21L, null);

        var dto = catalogo().obtenerCliente(21L);

        assertNull(dto.condicionIvaId());
        assertNull(dto.condicionIvaCodigo());
    }

    @Test
    void losPuntosDeVentaDicenSiElEmisorEsInscripto() {
        PuntoVenta central = new PuntoVenta();
        central.setId(1L);
        central.setNumero(1);
        central.setDescripcion("Sucursal Central");
        PuntoVenta ri = new PuntoVenta();
        ri.setId(2L);
        ri.setNumero(2);
        ri.setDescripcion("Sucursal Responsable Inscripto");
        ri.setResponsableInscripto(true);
        when(puntos.findAll()).thenReturn(List.of(ri, central));

        var lista = catalogo().obtenerPuntosVenta();

        assertEquals("1 - Sucursal Central (Monotributo)", lista.get(0).descripcion());
        assertFalse(lista.get(0).responsableInscripto());
        assertEquals("2 - Sucursal Responsable Inscripto (Resp. Inscripto)", lista.get(1).descripcion());
        assertTrue(lista.get(1).responsableInscripto());
    }

    @Test
    void elListadoYLaImpresionMuestranLaLetraYElIva() {
        puntoDeVenta(true);
        cliente(7L, inscripto);
        FacturaVenta factura = guardar(7L, inscripto.getId());
        factura.setFechaEmision(new java.util.Date());

        var reporte = TP.SpringBootGrupal.dtos.Factura.FacturaReporteDTO.toDTO(factura);
        var impresion = TP.SpringBootGrupal.dtos.Factura.FacturaImpresionDTO.toDTO(factura);

        assertEquals("A", reporte.getTipoComprobante());
        assertEquals("A", impresion.tipoComprobante());
        assertEquals(1000, impresion.importeNeto(), 0.001);
        assertEquals(210, impresion.importeIva(), 0.001);
    }

    @Test
    void unaFacturaViejaSinLetraSeMuestraComoC() {
        FacturaVenta vieja = new FacturaVenta();
        vieja.setCondicionIva(consumidorFinal);
        PuntoVenta punto = new PuntoVenta();
        vieja.setPuntoVenta(punto);

        assertEquals("C", TP.SpringBootGrupal.dtos.Factura.FacturaReporteDTO.toDTO(vieja).getTipoComprobante());
    }
}

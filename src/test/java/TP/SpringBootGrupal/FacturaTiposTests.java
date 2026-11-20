package TP.SpringBootGrupal;

import TP.SpringBootGrupal.arca.AfipWsaaClient;
import TP.SpringBootGrupal.arca.AfipWsfeClient;
import TP.SpringBootGrupal.arca.CredencialesArca;
import TP.SpringBootGrupal.arca.ResultadoCae;
import TP.SpringBootGrupal.arca.SolicitudFacturaC;
import TP.SpringBootGrupal.dtos.Factura.FacturaRequestDTO;
import TP.SpringBootGrupal.dtos.FacturaDetalle.FacturaDetalleRequestDTO;
import TP.SpringBootGrupal.entities.*;
import TP.SpringBootGrupal.repository.*;
import TP.SpringBootGrupal.service.FacturaServiceImpl;
import TP.SpringBootGrupal.service.FacturacionArcaService;
import TP.SpringBootGrupal.service.TipoComprobante;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
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
    void noPermiteGuardarUnaFacturaNuevaEnDolares() {
        TipoMoneda dolar = new TipoMoneda();
        dolar.setCodigoAfip("DOL");
        when(monedas.findById(1L)).thenReturn(Optional.of(dolar));
        puntoDeVenta(false);

        var error = assertThrows(IllegalArgumentException.class, () -> guardar(null, consumidorFinal.getId()));

        assertTrue(error.getMessage().contains("dólares"));
        verify(facturas, never()).save(any(FacturaVenta.class));
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

        var error = assertThrows(IllegalStateException.class, () -> arca.autorizarFacturaC(factura));

        assertTrue(error.getMessage().contains("Factura " + letra), error.getMessage());
        assertTrue(error.getMessage().contains("Responsable Inscripto"), error.getMessage());
        verifyNoInteractions(wsaa, wsfe);
    }

    @ParameterizedTest
    @CsvSource({"false,80,20123456786,1", "true,99,0,5"})
    void arcaUsaElDocumentoQueCorrespondeAlReceptor(boolean anonimo, int tipo, long documento, int iva,
                                                  @TempDir Path archivos) throws Exception {
        AfipWsaaClient wsaa = mock(AfipWsaaClient.class);
        AfipWsfeClient wsfe = mock(AfipWsfeClient.class);
        FacturacionArcaService arca = new FacturacionArcaService(wsaa, wsfe, facturas);
        Path clave = Files.createFile(archivos.resolve("clave"));
        Path certificado = Files.createFile(archivos.resolve("certificado"));
        long cuitEmisor = 20216874561L;
        ReflectionTestUtils.setField(arca, "cuit", cuitEmisor);
        ReflectionTestUtils.setField(arca, "rutaClavePrivada", clave.toString());
        ReflectionTestUtils.setField(arca, "rutaCertificado", certificado.toString());

        puntoDeVenta(false);
        Long clienteId = anonimo ? null : 9L;
        if (clienteId != null) {
            cliente(clienteId, inscripto).setCuit("20-12345678-6");
        }
        FacturaVenta factura = guardar(clienteId, anonimo ? consumidorFinal.getId() : inscripto.getId());
        factura.getTipoMoneda().setCodigoAfip("PES");
        when(facturas.findById(12L)).thenReturn(Optional.of(factura));
        var credenciales = new CredencialesArca("token", "firma");
        when(wsaa.obtenerCredenciales(clave, certificado)).thenReturn(credenciales);
        long ultimoNumero = anonimo ? 0L : 7L;
        when(wsfe.obtenerUltimoComprobante(credenciales, cuitEmisor, 1, 11)).thenReturn(ultimoNumero);
        if (ultimoNumero > 0L) {
            when(wsfe.consultarFechaComprobante(credenciales, cuitEmisor, 1, 11, ultimoNumero))
                    .thenReturn(new java.sql.Date(factura.getFechaEmision().getTime()).toLocalDate());
        }
        when(wsfe.solicitarCae(eq(credenciales), eq(cuitEmisor), any(SolicitudFacturaC.class)))
                .thenReturn(new ResultadoCae("A", "12345678901234", LocalDate.now(), List.of()));

        new TP.SpringBootGrupal.controller.ArcaRestController(arca).autorizar(12L);

        var solicitud = ArgumentCaptor.forClass(SolicitudFacturaC.class);
        verify(wsfe).solicitarCae(eq(credenciales), eq(cuitEmisor), solicitud.capture());
        assertEquals(tipo, solicitud.getValue().tipoDocumento());
        assertEquals(documento, solicitud.getValue().numeroDocumento());
        assertEquals(iva, solicitud.getValue().condicionIvaReceptorId());
        assertEquals(ultimoNumero + 1, solicitud.getValue().numeroComprobante());
        assertEquals(ultimoNumero + 1, factura.getNumero());
        if (anonimo) {
            verify(wsfe, never()).consultarFechaComprobante(any(), anyLong(), anyInt(), anyInt(), anyLong());
        }
        assertEquals("AUTORIZADA", factura.getEstado());
    }

    @ParameterizedTest
    @CsvSource({",10000000", ",10000001", "20ABC123456786,1200"})
    void arcaBloqueaCuitsInvalidosYConsumidoresAnonimosEnElLimite(String cuit, double importe) {
        AfipWsaaClient wsaa = mock(AfipWsaaClient.class);
        AfipWsfeClient wsfe = mock(AfipWsfeClient.class);
        FacturacionArcaService arca = new FacturacionArcaService(wsaa, wsfe, facturas);
        puntoDeVenta(false);
        Long clienteId = null;
        if (cuit != null) {
            clienteId = 9L;
            cliente(clienteId, monotributo).setCuit(cuit);
        }
        FacturaVenta factura = guardar(clienteId, monotributo.getId());
        factura.getTipoMoneda().setCodigoAfip("PES");
        factura.setImporteTotal(importe);

        var error = assertThrows(IllegalStateException.class, () -> arca.autorizarFacturaC(factura));

        assertTrue(error.getMessage().contains("CUIT"));
        assertEquals("PENDIENTE", factura.getEstado());
        verifyNoInteractions(wsaa, wsfe);
    }

    @Test
    void arcaBloqueaLaFechaAnteriorAlUltimoComprobanteDelPuntoDeVenta(@TempDir Path archivos) throws Exception {
        AfipWsaaClient wsaa = mock(AfipWsaaClient.class);
        AfipWsfeClient wsfe = mock(AfipWsfeClient.class);
        FacturacionArcaService arca = new FacturacionArcaService(wsaa, wsfe, facturas);
        Path clave = Files.createFile(archivos.resolve("clave"));
        Path certificado = Files.createFile(archivos.resolve("certificado"));
        long cuitEmisor = 20216874561L;
        ReflectionTestUtils.setField(arca, "cuit", cuitEmisor);
        ReflectionTestUtils.setField(arca, "rutaClavePrivada", clave.toString());
        ReflectionTestUtils.setField(arca, "rutaCertificado", certificado.toString());

        puntoDeVenta(false);
        cliente(9L, inscripto).setCuit("20123456786");
        FacturaVenta factura = guardar(9L, inscripto.getId());
        factura.getTipoMoneda().setCodigoAfip("PES");
        LocalDate ultimaFecha = new java.sql.Date(factura.getFechaEmision().getTime()).toLocalDate().plusDays(3);
        var credenciales = new CredencialesArca("token", "firma");
        when(wsaa.obtenerCredenciales(clave, certificado)).thenReturn(credenciales);
        when(wsfe.obtenerUltimoComprobante(credenciales, cuitEmisor, 1, 11)).thenReturn(9L);
        when(wsfe.consultarFechaComprobante(credenciales, cuitEmisor, 1, 11, 9L)).thenReturn(ultimaFecha);

        var error = assertThrows(IllegalStateException.class, () -> arca.autorizarFacturaC(factura));

        assertTrue(error.getMessage().contains(ultimaFecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        assertTrue(error.getMessage().contains("punto de venta 1"));
        assertEquals("PENDIENTE", factura.getEstado());
        verify(wsfe, never()).solicitarCae(any(), anyLong(), any());
    }

    @Test
    void arcaBloqueaUnaFacturaConFechaAnteriorAHoyAntesDeContactarElServicio() {
        AfipWsaaClient wsaa = mock(AfipWsaaClient.class);
        AfipWsfeClient wsfe = mock(AfipWsfeClient.class);
        FacturacionArcaService arca = new FacturacionArcaService(wsaa, wsfe, facturas);
        puntoDeVenta(false);
        cliente(9L, monotributo).setCuit("20123456786");
        FacturaVenta factura = guardar(9L, monotributo.getId());
        factura.getTipoMoneda().setCodigoAfip("PES");
        factura.setFechaEmision(java.sql.Date.valueOf(LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).minusDays(1)));

        var error = assertThrows(IllegalStateException.class, () -> arca.autorizarFacturaC(factura));

        assertTrue(error.getMessage().contains("anterior a hoy"));
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
        emanuel.setCuit("20334447775");

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
    void lasOpcionesDeIvaExcluyenSujetoExento() {
        var exento = condicion(14L, 4, "IVA Sujeto Exento");
        when(condiciones.findAll()).thenReturn(List.of(inscripto, exento, consumidorFinal, monotributo));
        assertEquals(List.of(11L, 10L, 12L),
                catalogo().obtenerCondicionesIva().stream().map(opcion -> opcion.id()).toList());
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

        assertEquals("1 - Sucursal Central", lista.get(0).descripcion());
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

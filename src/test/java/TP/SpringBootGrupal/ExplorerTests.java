package TP.SpringBootGrupal;

import TP.SpringBootGrupal.Exceptions.RecursoNoEncontradoException;
import TP.SpringBootGrupal.dtos.Factura.FacturaEdicionDTO;
import TP.SpringBootGrupal.dtos.Factura.FacturaRequestDTO;
import TP.SpringBootGrupal.dtos.FacturaDetalle.FacturaDetalleRequestDTO;
import TP.SpringBootGrupal.entities.*;
import TP.SpringBootGrupal.repository.*;
import TP.SpringBootGrupal.config.DatosIniciales;
import TP.SpringBootGrupal.dtos.OpcionDTO;
import TP.SpringBootGrupal.service.CatalogoService;
import TP.SpringBootGrupal.service.FacturaServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ExplorerTests {
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final ListaPrecioArticuloRepository precios = mock(ListaPrecioArticuloRepository.class);
    private final PuntoVentaRepository puntos = mock(PuntoVentaRepository.class);
    private final TipoMonedaRepository monedas = mock(TipoMonedaRepository.class);
    private final CondicionIvaRepository condiciones = mock(CondicionIvaRepository.class);
    private final FacturaVentaRepository facturas = mock(FacturaVentaRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final ListaPrecioRepository listas = mock(ListaPrecioRepository.class);
    private final ContactoRepository contactos = mock(ContactoRepository.class);
    private final DomicilioRepository domicilios = mock(DomicilioRepository.class);

    private ListaPrecioArticulo precio(long id, double importe) {
        Articulo articulo = new Articulo();
        articulo.setId(8L);
        articulo.setCodigo("A8");
        articulo.setDenominacion("Cuaderno");
        ListaPrecio lista = new ListaPrecio();
        lista.setDenominacion("Lista " + id);
        ListaPrecioArticulo precio = new ListaPrecioArticulo();
        precio.setId(id);
        precio.setArticulo(articulo);
        precio.setListaPrecio(lista);
        precio.setPrecioVenta(importe);
        return precio;
    }

    @ParameterizedTest
    @ValueSource(longs = {25, 26})
    void guardaLaEntradaDePrecioSeleccionadaParaElMismoArticulo(long id) {
        ListaPrecioArticulo elegido = precio(id, id == 25 ? 100 : 80);
        when(precios.findById(id)).thenReturn(Optional.of(elegido));
        when(usuarios.findById(1L)).thenReturn(Optional.of(new Usuario()));
        when(condiciones.findById(1L)).thenReturn(Optional.of(new CondicionIva()));
        when(monedas.findById(1L)).thenReturn(Optional.of(new TipoMoneda()));
        when(puntos.findById(1L)).thenReturn(Optional.of(new PuntoVenta()));
        when(facturas.save(any(FacturaVenta.class))).thenAnswer(invocacion -> {
            FacturaVenta factura = invocacion.getArgument(0);
            assertSame(elegido, factura.getDetalles().get(0).getListaPrecioArticulo());
            assertEquals(elegido.getPrecioVenta(), factura.getDetalles().get(0).getPrecioUnitario());
            return factura;
        });
        FacturaServiceImpl servicio = new FacturaServiceImpl(
                facturas, clientes, condiciones, monedas, puntos, precios, usuarios);
        var dto = new FacturaRequestDTO(null, 1L, 1L, 1L,
                List.of(new FacturaDetalleRequestDTO(id, 2.0, 0.0)));
        var resultado = servicio.save(dto, 1L, 0);
        assertEquals(elegido.getPrecioVenta() * 2, resultado.getImporteTotal());
        verify(precios, never()).findByArticulo_Id(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 41})
    void generaElNumeroSiguienteDelPuntoDeVenta(long ultimo) {
        when(precios.findById(25L)).thenReturn(Optional.of(precio(25, 100)));
        when(usuarios.findById(1L)).thenReturn(Optional.of(new Usuario()));
        when(condiciones.findById(1L)).thenReturn(Optional.of(new CondicionIva()));
        when(monedas.findById(1L)).thenReturn(Optional.of(new TipoMoneda()));
        when(puntos.findById(1L)).thenReturn(Optional.of(new PuntoVenta()));
        when(facturas.obtenerUltimoNumero(1L)).thenReturn(ultimo);
        when(facturas.save(any(FacturaVenta.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        var servicio = new FacturaServiceImpl(facturas, clientes, condiciones, monedas, puntos, precios, usuarios);
        var dto = new FacturaRequestDTO(null, 1L, 1L, 1L,
                List.of(new FacturaDetalleRequestDTO(25L, 1.0, 0.0)));
        var resultado = servicio.save(dto, 1L, 0);
        assertEquals(Long.valueOf(ultimo + 1), resultado.getNumeroFactura());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rechazaPrecioInexistenteODadoDeBaja(boolean baja) {
        ListaPrecioArticulo precio = precio(25, 100);
        precio.setFechaBaja(new Date());
        when(precios.findById(25L)).thenReturn(baja ? Optional.of(precio) : Optional.empty());
        when(usuarios.findById(1L)).thenReturn(Optional.of(new Usuario()));
        when(condiciones.findById(1L)).thenReturn(Optional.of(new CondicionIva()));
        when(monedas.findById(1L)).thenReturn(Optional.of(new TipoMoneda()));
        when(puntos.findById(1L)).thenReturn(Optional.of(new PuntoVenta()));
        var servicio = new FacturaServiceImpl(facturas, clientes, condiciones, monedas, puntos, precios, usuarios);
        var dto = new FacturaRequestDTO(null, 1L, 1L, 1L,
                List.of(new FacturaDetalleRequestDTO(25L, 1.0, 0.0)));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.save(dto, 1L, 0));
        verify(facturas, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {25, 26})
    void edicionConservaIdDePrecioEnLugarDeIdArticulo(long id) {
        FacturaVenta factura = new FacturaVenta();
        factura.setCondicionIva(new CondicionIva());
        factura.setTipoMoneda(new TipoMoneda());
        factura.setPuntoVenta(new PuntoVenta());
        FacturaVentaDetalle detalle = new FacturaVentaDetalle();
        detalle.setListaPrecioArticulo(precio(id, 100));
        factura.getDetalles().add(detalle);
        assertEquals(id, FacturaEdicionDTO.toDTO(factura).detalles().get(0).listaPrecioArticuloId());
    }

    private CatalogoService catalogo() {
        return new CatalogoService(puntos, monedas, condiciones, clientes, precios, listas);
    }

    @ParameterizedTest
    @ValueSource(strings = {"lib", "  lib  "})
    void buscaClientesEnElServidorConLimiteYExcluyeBajas(String texto) {
        Cliente activo = new Cliente();
        activo.setId(7L);
        activo.setDenominacion("Librería");
        activo.setCuitCuil("20123456789");
        Cliente baja = new Cliente();
        baja.setFechaBaja(new Date());
        when(clientes.buscar(eq("lib"), eq(PageRequest.of(0, CatalogoService.LIMITE_RESULTADOS))))
                .thenReturn(List.of(activo, baja));
        var resultado = catalogo().buscarClientes(texto);
        assertEquals(1, resultado.size());
        assertEquals(7L, resultado.get(0).id());
        assertEquals("Librería", resultado.get(0).denominacion());
        assertEquals("20123456789", resultado.get(0).cuitCuil());
        verify(clientes, never()).findAll();
    }

    @Test
    void buscaPreciosSoloDeLaListaElegidaYExcluyeBajas() {
        var primero = precio(25, 100);
        primero.getListaPrecio().setId(3L);
        var baja = precio(27, 90);
        baja.setFechaBaja(new Date());
        var articuloBaja = precio(28, 90);
        articuloBaja.getArticulo().setFechaBaja(new Date());
        when(precios.buscar(eq(3L), eq("cuad"), any(PageRequest.class)))
                .thenReturn(List.of(primero, baja, articuloBaja));
        var resultado = catalogo().buscarPreciosArticulos(3L, "cuad");
        assertEquals(List.of(25L), resultado.stream().map(p -> p.id()).toList());
        assertEquals(3L, resultado.get(0).listaPrecioId());
        assertEquals("Cuaderno", resultado.get(0).articuloDenominacion());
        verify(precios, never()).findAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ART-015", "  art-015 "})
    void buscaUnArticuloPorCodigoExactoEnLaListaElegida(String codigo) {
        var precio = precio(40, 1985);
        precio.getArticulo().setCodigo("ART-015");
        precio.getListaPrecio().setId(3L);
        when(precios.findFirstByListaPrecio_IdAndArticulo_CodigoIgnoreCase(3L, "ART-015"))
                .thenReturn(Optional.of(precio));
        var resultado = catalogo().obtenerPrecioPorCodigo(3L, codigo);
        assertEquals(40L, resultado.id());
        assertEquals(1985, resultado.precioVenta());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void codigoInexistenteODadoDeBajaNoSeEncuentra(boolean baja) {
        var precio = precio(40, 100);
        precio.getArticulo().setFechaBaja(new Date());
        when(precios.findFirstByListaPrecio_IdAndArticulo_CodigoIgnoreCase(3L, "ART-999"))
                .thenReturn(baja ? Optional.of(precio) : Optional.empty());
        var error = assertThrows(RecursoNoEncontradoException.class,
                () -> catalogo().obtenerPrecioPorCodigo(3L, "ART-999"));
        assertTrue(error.getMessage().contains("ART-999"));
    }

    @Test
    void elReporteIncluyeElCuitDelCliente() {
        FacturaVenta factura = new FacturaVenta();
        Cliente cliente = new Cliente();
        cliente.setDenominacion("Librería");
        cliente.setCuitCuil("20123456786");
        factura.setCliente(cliente);
        factura.setCondicionIva(new CondicionIva());
        factura.setPuntoVenta(new PuntoVenta());
        assertEquals("20123456786", TP.SpringBootGrupal.dtos.Factura.FacturaReporteDTO.toDTO(factura).getClienteCuitCuil());
    }

    @Test
    void buscarPreciosExigeListaDePrecios() {
        assertThrows(IllegalArgumentException.class, () -> catalogo().buscarPreciosArticulos(null, "x"));
    }

    @Test
    void explorersDevuelvenListaVaciaSinRegistros() {
        assertTrue(catalogo().buscarClientes("").isEmpty());
        assertTrue(catalogo().buscarPreciosArticulos(1L, "").isEmpty());
    }

    @Test
    void listasDePrecioSeDevuelvenComoOpciones() {
        ListaPrecio lista = new ListaPrecio();
        lista.setId(3L);
        lista.setDenominacion("Mayorista");
        when(listas.findByFechaBajaIsNullOrderByDenominacion()).thenReturn(List.of(lista));
        assertEquals(List.of(new OpcionDTO(3L, "Mayorista")), catalogo().obtenerListasPrecio());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 4})
    void cargaClientesDePruebaSoloSiNoHayNinguno(long existentes) {
        Usuario usuario = new Usuario();
        when(clientes.count()).thenReturn(existentes);
        when(usuarios.findAll()).thenReturn(List.of(usuario));
        new DatosIniciales(clientes, usuarios, contactos, domicilios).run();
        verify(clientes, times(existentes == 0 ? DatosIniciales.CLIENTES_PRUEBA.size() : 0))
                .save(any(Cliente.class));
    }

    @Test
    void noCargaClientesSiNoHayUsuarios() {
        when(clientes.count()).thenReturn(0L);
        when(usuarios.findAll()).thenReturn(List.of());
        new DatosIniciales(clientes, usuarios, contactos, domicilios).run();
        verify(clientes, never()).save(any(Cliente.class));
    }
}

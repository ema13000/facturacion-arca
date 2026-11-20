package TP.SpringBootGrupal.controller;

import TP.SpringBootGrupal.Exceptions.RecursoNoEncontradoException;
import TP.SpringBootGrupal.dtos.OpcionDTO;
import TP.SpringBootGrupal.dtos.ClienteExplorerDTO;
import TP.SpringBootGrupal.dtos.PrecioArticuloExplorerDTO;
import TP.SpringBootGrupal.service.CatalogoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalogos")
@CrossOrigin(origins = "*")
public class CatalogoRestController {

    private final CatalogoService service;

    public CatalogoRestController(CatalogoService service) {
        this.service = service;
    }

    // Búsqueda en el servidor: devuelve como máximo CatalogoService.LIMITE_RESULTADOS filas
    @GetMapping("/clientes")
    public List<ClienteExplorerDTO> buscarClientes(
            @RequestParam(value = "texto", defaultValue = "") String texto
    ) {
        return service.buscarClientes(texto);
    }

    @GetMapping("/clientes/{id}")
    public ClienteExplorerDTO obtenerCliente(@PathVariable("id") Long id) {
        return service.obtenerCliente(id);
    }

    @GetMapping("/listas-precio")
    public List<OpcionDTO> obtenerListasPrecio() {
        return service.obtenerListasPrecio();
    }

    @GetMapping("/precios-articulos")
    public List<PrecioArticuloExplorerDTO> buscarPreciosArticulos(
            @RequestParam("listaPrecioId") Long listaPrecioId,
            @RequestParam(value = "texto", defaultValue = "") String texto
    ) {
        return service.buscarPreciosArticulos(listaPrecioId, texto);
    }

    @GetMapping("/precios-articulos/por-codigo")
    public PrecioArticuloExplorerDTO obtenerPrecioPorCodigo(
            @RequestParam("listaPrecioId") Long listaPrecioId,
            @RequestParam("codigo") String codigo
    ) {
        return service.obtenerPrecioPorCodigo(listaPrecioId, codigo);
    }

    @GetMapping("/precios-articulos/{id}")
    public PrecioArticuloExplorerDTO obtenerPrecioArticulo(@PathVariable("id") Long id) {
        return service.obtenerPrecioArticulo(id);
    }

    @GetMapping("/puntos-venta")
    public List<TP.SpringBootGrupal.dtos.PuntoVentaDTO> obtenerPuntosVenta() {
        return service.obtenerPuntosVenta();
    }

    @GetMapping("/tipos-moneda")
    public List<OpcionDTO> obtenerTiposMoneda() {
        return service.obtenerTiposMoneda();
    }

    @GetMapping("/condiciones-iva")
    public List<OpcionDTO> obtenerCondicionesIva() {
        return service.obtenerCondicionesIva();
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> datoInvalido(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
    }
}

package TP.SpringBootGrupal.controller;

import TP.SpringBootGrupal.Exceptions.RecursoNoEncontradoException;
import TP.SpringBootGrupal.dtos.Factura.FacturaEstadoRequestDTO;
import TP.SpringBootGrupal.dtos.Factura.FacturaReporteDTO;
import TP.SpringBootGrupal.dtos.Factura.FacturaRequestDTO;
import TP.SpringBootGrupal.service.FacturaPdfGenerador;
import TP.SpringBootGrupal.service.FacturaServiceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import TP.SpringBootGrupal.dtos.Factura.FacturaEdicionDTO;

@RestController
@RequestMapping("/api/facturas")
@CrossOrigin(origins = "*", exposedHeaders = HttpHeaders.CONTENT_DISPOSITION)
public class FacturaRestController {

    private final FacturaServiceImpl service;
    private final FacturaPdfGenerador pdfGenerador;

    public FacturaRestController(FacturaServiceImpl facturaServiceImpl, FacturaPdfGenerador pdfGenerador) {
        this.service = facturaServiceImpl;
        this.pdfGenerador = pdfGenerador;
    }

    @GetMapping
    public ResponseEntity<List<FacturaReporteDTO>> findAll() {
        return ResponseEntity.ok(service.findAllParaListado());
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<FacturaReporteDTO> actualizarEstado(
            @PathVariable("id") Long id,
            @RequestParam("usuarioId") Long usuarioId,
            @RequestBody FacturaEstadoRequestDTO dto
    ) {
        if (!"ANULADA".equals(dto.estado())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Esta operación solo permite anular facturas"
            );
        }

        return ResponseEntity.ok(
                service.anular(id, usuarioId, dto.observaciones())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacturaReporteDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @GetMapping("/{id}/edicion")
    public ResponseEntity<FacturaEdicionDTO> findParaEdicion(
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(service.findParaEdicion(id));
    }

    @GetMapping("/{id}/impresion")
    public ResponseEntity<TP.SpringBootGrupal.dtos.Factura.FacturaImpresionDTO> findParaImpresion(
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(service.findParaImpresion(id));
    }

    /** PDF de una factura autorizada, con CAE y vencimiento. Una pendiente responde 409. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable("id") Long id) {
        var factura = service.findParaImpresion(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + FacturaPdfGenerador.nombreArchivo(factura) + "\"")
                .body(pdfGenerador.generar(factura));
    }

    @PostMapping
    public ResponseEntity<FacturaReporteDTO> save(
            @RequestBody FacturaRequestDTO dto,
            @RequestParam("usuarioId") Long usuarioId,
            @RequestParam("porcentajeIva") double porcentajeIva
    ) {
        FacturaReporteDTO creada = service.save(dto, usuarioId, porcentajeIva);

        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FacturaReporteDTO> update(
            @PathVariable("id") Long id,
            @RequestBody FacturaRequestDTO dto,
            @RequestParam("usuarioId") Long usuarioId,
            @RequestParam("porcentajeIva") double porcentajeIva
    ) {
        FacturaReporteDTO actualizada =
                service.update(dto, id, usuarioId, porcentajeIva);

        return ResponseEntity.ok(actualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") Long id,
            @RequestParam("usuarioId") Long usuarioId
    ) {
        service.delete(id, usuarioId);

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> recursoNoEncontrado(
            RecursoNoEncontradoException ex
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    // Operación no permitida por el estado de la factura (ej: editar una autorizada)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> operacionNoPermitida(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> datoInvalido(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
    }
}
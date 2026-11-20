package TP.SpringBootGrupal.controller;

import TP.SpringBootGrupal.arca.AutorizacionFacturaC;
import TP.SpringBootGrupal.service.FacturacionArcaService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/facturas")
@CrossOrigin(origins = "*")
public class ArcaRestController {

    private final FacturacionArcaService service;

    public ArcaRestController(FacturacionArcaService service) {
        this.service = service;
    }

    @PostMapping("/{id}/autorizar-arca")
    public ResponseEntity<AutorizacionFacturaC> autorizar(
            @PathVariable("id") Long facturaId
    ) throws Exception {

        return ResponseEntity.ok(service.autorizarFacturaPorId(facturaId));
    }

    // Factura que no se puede autorizar (ej: Factura A/B sin certificado de Responsable Inscripto)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<java.util.Map<String, String>> operacionNoPermitida(IllegalStateException ex) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body(java.util.Map.of("mensaje", ex.getMessage()));
    }
}

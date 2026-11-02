package TP.SpringBootGrupal.controller;

import TP.SpringBootGrupal.arca.AutorizacionFacturaC;
import TP.SpringBootGrupal.arca.SolicitudAutorizacionArca;
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
            @PathVariable("id") Long facturaId,
            @RequestBody SolicitudAutorizacionArca solicitud
    ) throws Exception {

        AutorizacionFacturaC resultado =
                service.autorizarFacturaPorId(
                        facturaId,
                        solicitud.tipoDocumento(),
                        solicitud.numeroDocumento()
                );

        return ResponseEntity.ok(resultado);
    }
}
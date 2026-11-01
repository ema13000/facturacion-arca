package TP.SpringBootGrupal.service;

import TP.SpringBootGrupal.arca.AfipWsaaClient;
import TP.SpringBootGrupal.arca.AfipWsfeClient;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.Files;
import java.nio.file.Path;
import TP.SpringBootGrupal.entities.FacturaVenta;

import TP.SpringBootGrupal.arca.SolicitudFacturaC;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import TP.SpringBootGrupal.arca.AutorizacionFacturaC;
import TP.SpringBootGrupal.arca.CredencialesArca;
import TP.SpringBootGrupal.arca.ResultadoCae;

import TP.SpringBootGrupal.repository.FacturaVentaRepository;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacturacionArcaService {

    private final AfipWsaaClient wsaaClient;
    private final AfipWsfeClient wsfeClient;
    private final FacturaVentaRepository facturaRepository;

    @Value("${arca.cuit}")
    private long cuit;

    @Value("${arca.clave-privada}")
    private String rutaClavePrivada;

    @Value("${arca.certificado}")
    private String rutaCertificado;

    public FacturacionArcaService(
            AfipWsaaClient wsaaClient,
            AfipWsfeClient wsfeClient,
            FacturaVentaRepository facturaRepository
    ) {
        this.wsaaClient = wsaaClient;
        this.wsfeClient = wsfeClient;
        this.facturaRepository = facturaRepository;
    }

    private void validarConfiguracion() {
        if (cuit <= 0 || String.valueOf(cuit).length() != 11) {
            throw new IllegalStateException(
                    "Configurá ARCA_CUIT con el CUIT del emisor de 11 dígitos"
            );
        }

        if (rutaClavePrivada == null || rutaClavePrivada.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar ARCA_CLAVE_PRIVADA"
            );
        }

        if (rutaCertificado == null || rutaCertificado.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar ARCA_CERTIFICADO"
            );
        }

        if (!Files.isRegularFile(Path.of(rutaClavePrivada))
                || !Files.isReadable(Path.of(rutaClavePrivada))) {
            throw new IllegalStateException(
                    "El archivo de la clave privada no existe o no se puede leer"
            );
        }

        if (!Files.isRegularFile(Path.of(rutaCertificado))
                || !Files.isReadable(Path.of(rutaCertificado))) {
            throw new IllegalStateException(
                    "El archivo del certificado no existe o no se puede leer"
            );
        }
    }

    private void validarFacturaParaArca(FacturaVenta factura) {
        if (factura == null) {
            throw new IllegalArgumentException("La factura es obligatoria");
        }

        if (!"PENDIENTE".equals(factura.getEstado())
                || factura.getFechaBaja() != null) {
            throw new IllegalStateException(
                    "Solo se pueden autorizar facturas pendientes y activas"
            );
        }

        if (factura.getCae() != null && !factura.getCae().isBlank()) {
            throw new IllegalStateException(
                    "La factura ya tiene un CAE"
            );
        }

        if (factura.getTipoMoneda() == null
                || !"PES".equals(factura.getTipoMoneda().getCodigoAfip())) {
            throw new IllegalStateException(
                    "Esta integración solo admite facturas en pesos (PES)"
            );
        }

        if (factura.getPuntoVenta() == null
                || factura.getPuntoVenta().getNumero() <= 0) {
            throw new IllegalStateException(
                    "La factura debe tener un punto de venta válido"
            );
        }

        if (factura.getFechaEmision() == null
                || factura.getCondicionIva() == null) {
            throw new IllegalStateException(
                    "La factura debe tener fecha y condición de IVA del receptor"
            );
        }

        if (!Double.isFinite(factura.getImporteTotal())
                || factura.getImporteTotal() <= 0) {
            throw new IllegalStateException(
                    "El importe total debe ser un número positivo"
            );
        }

        if (factura.getDetalles().isEmpty()) {
            throw new IllegalStateException(
                    "La factura debe tener al menos un detalle"
            );
        }

        boolean tieneIvaDiscriminado = factura.getDetalles().stream()
                .anyMatch(detalle -> detalle.getImporteIva() != 0);

        if (tieneIvaDiscriminado) {
            throw new IllegalStateException(
                    "Esta integración de Factura C no admite detalles con IVA discriminado"
            );
        }
    }

    private SolicitudFacturaC prepararSolicitud(
            FacturaVenta factura,
            long numeroComprobante,
            int tipoDocumento,
            long numeroDocumento
    ) {
        validarFacturaParaArca(factura);

        if (numeroComprobante <= 0) {
            throw new IllegalArgumentException(
                    "El número de comprobante debe ser positivo"
            );
        }

        LocalDate fecha = new java.sql.Date(
                factura.getFechaEmision().getTime()
        ).toLocalDate();

        BigDecimal total = BigDecimal.valueOf(
                factura.getImporteTotal()
        ).setScale(2, RoundingMode.UNNECESSARY);

        return new SolicitudFacturaC(
                factura.getPuntoVenta().getNumero(),
                numeroComprobante,
                fecha,
                tipoDocumento,
                numeroDocumento,
                factura.getCondicionIva().getCodigoAfip(),
                total
        );
    }

    public AutorizacionFacturaC autorizarFacturaC(
            FacturaVenta factura,
            int tipoDocumento,
            long numeroDocumento
    ) throws Exception {

        validarConfiguracion();
        validarFacturaParaArca(factura);

        CredencialesArca credenciales = wsaaClient.obtenerCredenciales(
                Path.of(rutaClavePrivada),
                Path.of(rutaCertificado)
        );

        long ultimoNumero = wsfeClient.obtenerUltimoComprobante(
                credenciales,
                cuit,
                factura.getPuntoVenta().getNumero(),
                11
        );

        long siguienteNumero = Math.addExact(ultimoNumero, 1L);

        SolicitudFacturaC solicitud = prepararSolicitud(
                factura,
                siguienteNumero,
                tipoDocumento,
                numeroDocumento
        );

        ResultadoCae resultado = wsfeClient.solicitarCae(
                credenciales,
                cuit,
                solicitud
        );

        AutorizacionFacturaC autorizacion =
                new AutorizacionFacturaC(siguienteNumero, resultado);

        aplicarResultado(factura, autorizacion);

        return autorizacion;
    }

    private void aplicarResultado(
            FacturaVenta factura,
            AutorizacionFacturaC autorizacion
    ) {
        ResultadoCae resultado = autorizacion.resultado();

        factura.setResultadoAfip(resultado.resultado());

        if ("A".equals(resultado.resultado())) {
            factura.setNumero(autorizacion.numeroComprobante());
            factura.setCae(resultado.cae());

            factura.setCaeFechaVencimiento(
                    java.sql.Date.valueOf(resultado.fechaVencimiento())
            );

            factura.setEstado("AUTORIZADA");
            factura.setMotivoRechazo(null);
        } else {
            factura.setMotivoRechazo(
                    String.join(" | ", resultado.observaciones())
            );
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AutorizacionFacturaC autorizarFacturaPorId(
            Long facturaId,
            int tipoDocumento,
            long numeroDocumento
    ) throws Exception {

        FacturaVenta factura = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la factura con ID " + facturaId
                ));

        AutorizacionFacturaC autorizacion = autorizarFacturaC(
                factura,
                tipoDocumento,
                numeroDocumento
        );

        facturaRepository.save(factura);

        return autorizacion;
    }
}
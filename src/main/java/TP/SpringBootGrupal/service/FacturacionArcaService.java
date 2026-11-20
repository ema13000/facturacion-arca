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
import java.time.format.DateTimeFormatter;

import TP.SpringBootGrupal.arca.AutorizacionFacturaC;
import TP.SpringBootGrupal.arca.CredencialesArca;
import TP.SpringBootGrupal.arca.ResultadoCae;

import TP.SpringBootGrupal.repository.FacturaVentaRepository;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacturacionArcaService {

    // RG 5700. A partir de este importe en pesos se debe identificar al consumidor final.
    private static final double LIMITE_CONSUMIDOR_FINAL_SIN_IDENTIFICAR = 10_000_000;
    private static final int CODIGO_FACTURA_C = 11;
    private static final DateTimeFormatter FECHA_MENSAJE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

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

    /** Solo la Factura C está integrada: A y B necesitan un CUIT Responsable Inscripto con certificado. */
    private void validarLetraAutorizable(FacturaVenta factura) {
        if (factura == null) {
            throw new IllegalArgumentException("La factura es obligatoria");
        }
        String letra = TipoComprobante.normalizar(factura.getTipoComprobante());
        if (!"C".equals(letra)) {
            throw new IllegalStateException(
                    "Para autorizar la Factura " + letra + " en ARCA hace falta un CUIT Responsable Inscripto"
                            + " con su certificado. La factura queda guardada como PENDIENTE."
            );
        }
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

        if (fechaEmision(factura).isBefore(LocalDate.now(FacturaServiceImpl.ZONA_ARGENTINA))) {
            throw new IllegalStateException("La fecha de emisión es anterior a hoy. Editá la factura antes de enviarla a ARCA.");
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

    private LocalDate fechaEmision(FacturaVenta factura) {
        return new java.sql.Date(factura.getFechaEmision().getTime()).toLocalDate();
    }

    private SolicitudFacturaC prepararSolicitud(
            FacturaVenta factura,
            long numeroComprobante,
            long numeroDocumento
    ) {
        if (numeroComprobante <= 0) {
            throw new IllegalArgumentException(
                    "El número de comprobante debe ser positivo"
            );
        }

        BigDecimal total = BigDecimal.valueOf(
                factura.getImporteTotal()
        ).setScale(2, RoundingMode.UNNECESSARY);

        return new SolicitudFacturaC(
                factura.getPuntoVenta().getNumero(),
                numeroComprobante,
                fechaEmision(factura),
                numeroDocumento,
                factura.getCondicionIva().getCodigoAfip(),
                total
        );
    }

    public AutorizacionFacturaC autorizarFacturaC(FacturaVenta factura) throws Exception {
        validarLetraAutorizable(factura);
        validarFacturaParaArca(factura);

        long numeroDocumento = documentoReceptor(factura);
        validarConfiguracion();

        CredencialesArca credenciales = wsaaClient.obtenerCredenciales(
                Path.of(rutaClavePrivada),
                Path.of(rutaCertificado)
        );

        int puntoVenta = factura.getPuntoVenta().getNumero();
        long ultimoNumero = wsfeClient.obtenerUltimoComprobante(credenciales, cuit, puntoVenta, CODIGO_FACTURA_C);
        if (ultimoNumero > 0) {
            LocalDate ultimaFecha = wsfeClient.consultarFechaComprobante(
                    credenciales, cuit, puntoVenta, CODIGO_FACTURA_C, ultimoNumero);
            if (fechaEmision(factura).isBefore(ultimaFecha)) {
                throw new IllegalStateException(
                        "La fecha de emisión es anterior a la última Factura C autorizada por ARCA "
                                + "(n.º " + ultimoNumero + ", punto de venta " + puntoVenta + "). "
                                + "La fecha mínima para este punto de venta es " + ultimaFecha.format(FECHA_MENSAJE) + ".");
            }
        }
        long siguienteNumero = Math.addExact(ultimoNumero, 1L);

        SolicitudFacturaC solicitud = prepararSolicitud(
                factura,
                siguienteNumero,
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

    private long documentoReceptor(FacturaVenta factura) {
        if (factura.getCliente() != null) {
            return factura.getCliente().numeroCuit();
        }
        if (factura.getCondicionIva().getCodigoAfip() != TipoComprobante.CONSUMIDOR_FINAL) {
            throw new IllegalStateException("Seleccioná un cliente con CUIT para esta condición de IVA");
        }
        if (factura.getImporteTotal() >= LIMITE_CONSUMIDOR_FINAL_SIN_IDENTIFICAR) {
            throw new IllegalStateException("Seleccioná un consumidor final con CUIT para facturas desde $10.000.000");
        }
        return 0;
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
    public AutorizacionFacturaC autorizarFacturaPorId(Long facturaId) throws Exception {

        FacturaVenta factura = facturaRepository.findById(facturaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la factura con ID " + facturaId
                ));

        AutorizacionFacturaC autorizacion = autorizarFacturaC(factura);

        facturaRepository.save(factura);

        return autorizacion;
    }
}
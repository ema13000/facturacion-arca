package TP.SpringBootGrupal.arca;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.time.format.DateTimeFormatter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;

@Component
public class AfipWsfeClient {

    private static final String URL_WSFE_HOMO = "https://wswhomo.afip.gov.ar/wsfev1/service.asmx";

    private String escaparXml(String texto) {
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String crearConsultaUltimoComprobante(
            CredencialesArca credenciales,
            long cuit,
            int puntoVenta,
            int tipoComprobante
    ) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope
                xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                xmlns:fe="http://ar.gov.afip.dif.FEV1/">
                <soap:Header/>
                <soap:Body>
                    <fe:FECompUltimoAutorizado>
                        <fe:Auth>
                            <fe:Token>%s</fe:Token>
                            <fe:Sign>%s</fe:Sign>
                            <fe:Cuit>%d</fe:Cuit>
                        </fe:Auth>
                        <fe:PtoVta>%d</fe:PtoVta>
                        <fe:CbteTipo>%d</fe:CbteTipo>
                    </fe:FECompUltimoAutorizado>
                </soap:Body>
            </soap:Envelope>
            """.formatted(
                escaparXml(credenciales.token()),
                escaparXml(credenciales.sign()),
                cuit,
                puntoVenta,
                tipoComprobante
        );
    }

    private String enviarSoap(
            String mensajeSoap,
            String operacion
    ) throws Exception {

        HttpClient cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create(URL_WSFE_HOMO))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header(
                        "SOAPAction",
                        "\"http://ar.gov.afip.dif.FEV1/" + operacion + "\""
                )
                .POST(HttpRequest.BodyPublishers.ofString(
                        mensajeSoap,
                        StandardCharsets.UTF_8
                ))
                .build();

        HttpResponse<String> respuesta = cliente.send(
                peticion,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (respuesta.statusCode() != 200) {
            throw new IllegalStateException(
                    "WSFE respondió con HTTP " + respuesta.statusCode()
            );
        }

        return respuesta.body();
    }

    private long extraerUltimoNumero(String respuestaSoap) throws Exception {
        Document documento = leerXml(respuestaSoap);

        NodeList errores = documento.getElementsByTagNameNS("*", "Err");

        if (errores.getLength() > 0) {
            throw new IllegalStateException(
                    "ARCA rechazó la consulta: "
                            + errores.item(0).getTextContent().trim()
            );
        }

        NodeList numeros = documento.getElementsByTagNameNS(
                "*", "CbteNro"
        );

        if (numeros.getLength() != 1) {
            throw new IllegalStateException(
                    "La respuesta no contiene un número de comprobante único"
            );
        }

        long numero = Long.parseLong(
                numeros.item(0).getTextContent().trim()
        );

        if (numero < 0) {
            throw new IllegalStateException(
                    "ARCA devolvió un número de comprobante inválido"
            );
        }

        return numero;
    }

    public long obtenerUltimoComprobante(
            CredencialesArca credenciales,
            long cuit,
            int puntoVenta,
            int tipoComprobante
    ) throws Exception {

        String solicitud = crearConsultaUltimoComprobante(
                credenciales,
                cuit,
                puntoVenta,
                tipoComprobante
        );

        String respuesta = enviarSoap(
                solicitud,
                "FECompUltimoAutorizado"
        );

        return extraerUltimoNumero(respuesta);
    }

    private String crearSolicitudCae(
            CredencialesArca credenciales,
            long cuit,
            SolicitudFacturaC factura
    ) {
        String fecha = factura.fechaEmision()
                .format(DateTimeFormatter.BASIC_ISO_DATE);

        String importe = factura.importeTotal().toPlainString();

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <soap:Envelope
                xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
                xmlns:fe="http://ar.gov.afip.dif.FEV1/">
                <soap:Header/>
                <soap:Body>
                    <fe:FECAESolicitar>
                        <fe:Auth>
                            <fe:Token>%s</fe:Token>
                            <fe:Sign>%s</fe:Sign>
                            <fe:Cuit>%d</fe:Cuit>
                        </fe:Auth>
                        <fe:FeCAEReq>
                            <fe:FeCabReq>
                                <fe:CantReg>1</fe:CantReg>
                                <fe:PtoVta>%d</fe:PtoVta>
                                <fe:CbteTipo>11</fe:CbteTipo>
                            </fe:FeCabReq>
                            <fe:FeDetReq>
                                <fe:FECAEDetRequest>
                                    <fe:Concepto>1</fe:Concepto>
                                    <fe:DocTipo>%d</fe:DocTipo>
                                    <fe:DocNro>%d</fe:DocNro>
                                    <fe:CbteDesde>%d</fe:CbteDesde>
                                    <fe:CbteHasta>%d</fe:CbteHasta>
                                    <fe:CbteFch>%s</fe:CbteFch>
                                    <fe:ImpTotal>%s</fe:ImpTotal>
                                    <fe:ImpTotConc>0.00</fe:ImpTotConc>
                                    <fe:ImpNeto>%s</fe:ImpNeto>
                                    <fe:ImpOpEx>0.00</fe:ImpOpEx>
                                    <fe:ImpTrib>0.00</fe:ImpTrib>
                                    <fe:ImpIVA>0.00</fe:ImpIVA>
                                    <fe:MonId>PES</fe:MonId>
                                    <fe:MonCotiz>1</fe:MonCotiz>
                                    <fe:CondicionIVAReceptorId>%d</fe:CondicionIVAReceptorId>
                                </fe:FECAEDetRequest>
                            </fe:FeDetReq>
                        </fe:FeCAEReq>
                    </fe:FECAESolicitar>
                </soap:Body>
            </soap:Envelope>
            """.formatted(
                escaparXml(credenciales.token()),
                escaparXml(credenciales.sign()),
                cuit,
                factura.puntoVenta(),
                factura.tipoDocumento(),
                factura.numeroDocumento(),
                factura.numeroComprobante(),
                factura.numeroComprobante(),
                fecha,
                importe,
                importe,
                factura.condicionIvaReceptorId()
        );
    }

    private Document leerXml(String xml) throws Exception {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        factory.setNamespaceAware(true);
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        return factory.newDocumentBuilder().parse(
                new InputSource(new StringReader(xml))
        );
    }

    private ResultadoCae extraerResultadoCae(
            String respuestaSoap
    ) throws Exception {

        Document documento = leerXml(respuestaSoap);

        NodeList errores = documento.getElementsByTagNameNS("*", "Err");

        if (errores.getLength() > 0) {
            throw new IllegalStateException(
                    "ARCA informó un error: "
                            + errores.item(0).getTextContent().trim()
            );
        }

        NodeList detalles = documento.getElementsByTagNameNS(
                "*", "FECAEDetResponse"
        );

        if (detalles.getLength() != 1) {
            throw new IllegalStateException(
                    "Se esperaba la respuesta de un único comprobante"
            );
        }

        Element detalle = (Element) detalles.item(0);

        NodeList resultados = detalle.getElementsByTagNameNS(
                "*", "Resultado"
        );

        if (resultados.getLength() != 1) {
            throw new IllegalStateException(
                    "La respuesta no contiene el resultado del comprobante"
            );
        }

        String resultado = resultados.item(0).getTextContent().trim();

        List<String> observaciones = new ArrayList<>();

        NodeList mensajes = detalle.getElementsByTagNameNS("*", "Obs");

        for (int i = 0; i < mensajes.getLength(); i++) {
            observaciones.add(mensajes.item(i).getTextContent().trim());
        }

        String cae = null;
        LocalDate vencimiento = null;

        if ("A".equals(resultado)) {
            NodeList codigos = detalle.getElementsByTagNameNS("*", "CAE");
            NodeList fechas = detalle.getElementsByTagNameNS(
                    "*", "CAEFchVto"
            );

            if (codigos.getLength() != 1 || fechas.getLength() != 1) {
                throw new IllegalStateException(
                        "La autorización no contiene CAE o vencimiento"
                );
            }

            cae = codigos.item(0).getTextContent().trim();

            if (cae.isBlank()) {
                throw new IllegalStateException(
                        "ARCA devolvió un CAE vacío"
                );
            }

            vencimiento = LocalDate.parse(
                    fechas.item(0).getTextContent().trim(),
                    DateTimeFormatter.BASIC_ISO_DATE
            );
        } else if (!"R".equals(resultado)) {
            throw new IllegalStateException(
                    "Resultado de autorización desconocido: " + resultado
            );
        }

        return new ResultadoCae(
                resultado,
                cae,
                vencimiento,
                List.copyOf(observaciones)
        );
    }

    public ResultadoCae solicitarCae(
            CredencialesArca credenciales,
            long cuit,
            SolicitudFacturaC factura
    ) throws Exception {

        String solicitud = crearSolicitudCae(
                credenciales,
                cuit,
                factura
        );

        String respuesta = enviarSoap(
                solicitud,
                "FECAESolicitar"
        );

        return extraerResultadoCae(respuesta);
    }
}
package TP.SpringBootGrupal.arca;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Collections;
import java.util.HexFormat;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

@Component
public class AfipWsaaClient {

    private final CacheTicketsWsaa cache = new CacheTicketsWsaa(directorioTickets(), Clock.systemUTC());

    private static Path directorioTickets() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path perfil = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".springbootgrupal")
                : Path.of(localAppData, "SpringBootGrupal");
        return perfil.resolve("wsaa-homologacion-wsfe");
    }

    public String generarTra() {
        ZonedDateTime ahora = ZonedDateTime.now().withNano(0);

        long identificador = ahora.toEpochSecond();

        String generacion = ahora.minusMinutes(5)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String vencimiento = ahora.plusMinutes(10)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <loginTicketRequest version="1.0">
                    <header>
                        <destination>cn=wsaahomo,o=afip,c=ar,serialNumber=CUIT 33693450239</destination>
                        <uniqueId>%d</uniqueId>
                        <generationTime>%s</generationTime>
                        <expirationTime>%s</expirationTime>
                    </header>
                    <service>wsfe</service>
                </loginTicketRequest>
                """.formatted(identificador, generacion, vencimiento);
    }

    private PrivateKey cargarClavePrivada(Path ruta) throws Exception {
        String contenido = Files.readString(ruta);

        String contenidoBase64 = contenido
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] bytes = Base64.getDecoder().decode(contenidoBase64);

        PKCS8EncodedKeySpec especificacion =
                new PKCS8EncodedKeySpec(bytes);

        return KeyFactory.getInstance("RSA")
                .generatePrivate(especificacion);
    }

    private X509Certificate cargarCertificado(Path ruta) throws Exception {
        try (InputStream archivo = Files.newInputStream(ruta)) {
            CertificateFactory factory =
                    CertificateFactory.getInstance("X.509");

            return (X509Certificate) factory.generateCertificate(archivo);
        }
    }

    private String firmarTra(
            String tra,
            PrivateKey clavePrivada,
            X509Certificate certificado
    ) throws Exception {

        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        ContentSigner firmante =
                new JcaContentSignerBuilder("SHA256withRSA")
                        .setProvider("BC")
                        .build(clavePrivada);

        CMSSignedDataGenerator generador =
                new CMSSignedDataGenerator();

        generador.addSignerInfoGenerator(
                new JcaSignerInfoGeneratorBuilder(
                        new JcaDigestCalculatorProviderBuilder()
                                .setProvider("BC")
                                .build()
                ).build(firmante, certificado)
        );

        generador.addCertificates(
                new JcaCertStore(Collections.singletonList(certificado))
        );

        CMSProcessableByteArray contenido =
                new CMSProcessableByteArray(
                        tra.getBytes(StandardCharsets.UTF_8)
                );

        CMSSignedData firmado = generador.generate(contenido, true);

        return Base64.getEncoder()
                .encodeToString(firmado.getEncoded());
    }

    private String crearMensajeSoap(String cmsBase64) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <soapenv:Envelope
                xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                xmlns:wsaa="http://wsaa.view.sua.dvadac.desein.afip.gov">
                <soapenv:Header/>
                <soapenv:Body>
                    <wsaa:loginCms>
                        <wsaa:in0>%s</wsaa:in0>
                    </wsaa:loginCms>
                </soapenv:Body>
            </soapenv:Envelope>
            """.formatted(cmsBase64);
    }

    private CacheTicketsWsaa.Ticket enviarSoap(String mensajeSoap) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create(
                        "https://wsaahomo.afip.gov.ar/ws/services/LoginCms"
                ))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mensajeSoap,
                        StandardCharsets.UTF_8
                ))
                .build();

        HttpResponse<String> respuesta = ConexionArca.HTTP.send(
                peticion,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        return procesarRespuesta(respuesta.statusCode(), respuesta.body());
    }

    CacheTicketsWsaa.Ticket procesarRespuesta(int estado, String respuestaSoap) throws Exception {
        Document soap;
        try {
            soap = leerXml(respuestaSoap);
        } catch (SAXException | java.io.IOException error) {
            throw new IllegalStateException("WSAA respondió con HTTP " + estado, error);
        }

        NodeList fallos = soap.getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/", "Fault");
        if (fallos.getLength() > 0) {
            Element fallo = (Element) fallos.item(0);
            String codigo = texto(fallo, "faultcode").replaceFirst("^.*:", "");
            String explicacion = switch (codigo) {
                case "coe.alreadyAuthenticated" -> "Ya existe un ticket vigente fuera de esta sesión. "
                        + "Si se generó con otra aplicación, usá el ticket de esa aplicación o esperá su vencimiento.";
                case "coe.notAuthorized" -> "El certificado de homologación debe estar asociado al servicio wsfe en WSASS.";
                case "cms.cert.expired", "cms.cert.invalid", "cms.cert.untrusted" ->
                        "Verificá la vigencia del certificado y que haya sido emitido para homologación.";
                case "cms.sign.invalid" -> "Verificá que la clave privada corresponda al certificado de homologación.";
                default -> texto(fallo, "faultstring");
            };
            throw new IllegalStateException("WSAA " + codigo + ": " + explicacion);
        }
        if (estado != 200) {
            throw new IllegalStateException("WSAA respondió con HTTP " + estado);
        }
        return extraerTicket(soap);
    }

    private String texto(Element elemento, String etiqueta) {
        var resultados = elemento.getElementsByTagNameNS("*", etiqueta);
        return resultados.getLength() == 0 ? "" : resultados.item(0).getTextContent().strip();
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

        var lector = factory.newDocumentBuilder();
        lector.setErrorHandler(new DefaultHandler());
        return lector.parse(new InputSource(new StringReader(xml)));
    }

    private CacheTicketsWsaa.Ticket extraerTicket(Document soap) throws Exception {

        NodeList resultados = soap.getElementsByTagNameNS(
                "*", "loginCmsReturn"
        );

        if (resultados.getLength() != 1) {
            throw new IllegalStateException(
                    "La respuesta de WSAA no contiene loginCmsReturn"
            );
        }

        String resultadoXml = resultados.item(0).getTextContent();
        Document resultado = leerXml(resultadoXml);

        NodeList tokens = resultado.getElementsByTagNameNS("*", "token");
        NodeList firmas = resultado.getElementsByTagNameNS("*", "sign");

        if (tokens.getLength() != 1 || firmas.getLength() != 1) {
            throw new IllegalStateException(
                    "La respuesta de WSAA no contiene token y sign"
            );
        }

        String token = tokens.item(0).getTextContent().trim();
        String sign = firmas.item(0).getTextContent().trim();

        if (token.isBlank() || sign.isBlank()) {
            throw new IllegalStateException(
                    "WSAA devolvió credenciales vacías"
            );
        }

        String vencimiento = texto(resultado.getDocumentElement(), "expirationTime");
        if (vencimiento.isBlank()) {
            throw new IllegalStateException("WSAA devolvió un ticket sin fecha de vencimiento");
        }
        return new CacheTicketsWsaa.Ticket(new CredencialesArca(token, sign),
                OffsetDateTime.parse(vencimiento).toInstant());
    }

    public CredencialesArca obtenerCredenciales(
            Path rutaClavePrivada,
            Path rutaCertificado
    ) throws Exception {

        X509Certificate certificado = cargarCertificado(rutaCertificado);
        String huella = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificado.getEncoded()));
        return cache.obtener(huella, () -> {
            PrivateKey clavePrivada = cargarClavePrivada(rutaClavePrivada);
            String cms = firmarTra(generarTra(), clavePrivada, certificado);
            return enviarSoap(crearMensajeSoap(cms));
        });
    }

}
package TP.SpringBootGrupal;

import TP.SpringBootGrupal.dtos.Factura.FacturaImpresionDTO;
import TP.SpringBootGrupal.controller.FacturaRestController;
import TP.SpringBootGrupal.service.FacturaPdfGenerador;
import TP.SpringBootGrupal.service.FacturaServiceImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FacturaPdfTests {

    private final FacturaPdfGenerador generador = new FacturaPdfGenerador();

    private Date fecha(int anio, int mes, int dia) {
        return new GregorianCalendar(anio, mes - 1, dia).getTime();
    }

    private FacturaImpresionDTO factura(String cliente, String cuit, String cae, Date vencimiento) {
        return new FacturaImpresionDTO(
                7L, 25L, 3, "Casa central", fecha(2026, 11, 10),
                cliente, cuit, "Consumidor Final", 1500.0, cae, vencimiento,
                List.of(new FacturaImpresionDTO.Renglon("Cuaderno A4", 2, 750.0, 0, 1500.0)),
                "C", 1500.0, 0.0
        );
    }

    private String texto(byte[] pdf) throws Exception {
        PdfReader lector = new PdfReader(pdf);
        StringBuilder texto = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(lector);
        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            texto.append(extractor.getTextFromPage(pagina));
        }
        lector.close();
        return texto.toString();
    }

    @Test
    void generaUnPdfConCaeVencimientoYNumeroDeComprobante() throws Exception {
        byte[] pdf = generador.generar(factura("Librería Centro", "20123456786",
                "74123456789012", fecha(2026, 11, 20)));

        assertEquals("%PDF", new String(pdf, 0, 4));
        String contenido = texto(pdf);
        assertTrue(contenido.contains("74123456789012"), contenido);
        assertTrue(contenido.contains("20/11/2026"), contenido);
        assertTrue(contenido.contains("0003-00000025"), contenido);
        assertTrue(contenido.contains("Librería Centro"), contenido);
        assertTrue(contenido.contains("Cuaderno A4"), contenido);
    }

    @Test
    void otraFacturaMuestraSusPropiosDatos() throws Exception {
        byte[] pdf = generador.generar(factura("Consumidor Final", null,
                "61999888777666", fecha(2027, 1, 5)));

        String contenido = texto(pdf);
        assertTrue(contenido.contains("61999888777666"), contenido);
        assertTrue(contenido.contains("05/01/2027"), contenido);
        assertTrue(contenido.contains("Consumidor Final"), contenido);
        assertFalse(contenido.contains("74123456789012"));
    }

    @Test
    void elEndpointDevuelveElPdfComoDescarga() throws Exception {
        FacturaServiceImpl servicio = mock(FacturaServiceImpl.class);
        when(servicio.findParaImpresion(7L)).thenReturn(factura("Escuela Norte", "27234567891",
                "70000000000001", fecha(2026, 12, 1)));
        FacturaRestController controlador = new FacturaRestController(servicio, generador);

        ResponseEntity<byte[]> respuesta = controlador.descargarPdf(7L);

        assertEquals(MediaType.APPLICATION_PDF, respuesta.getHeaders().getContentType());
        assertEquals("attachment; filename=\"factura-C-0003-00000025.pdf\"",
                respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertTrue(texto(respuesta.getBody()).contains("70000000000001"));
    }

    @Test
    void elEndpointNoGeneraPdfDeUnaFacturaPendiente() {
        FacturaServiceImpl servicio = mock(FacturaServiceImpl.class);
        when(servicio.findParaImpresion(9L))
                .thenThrow(new IllegalStateException("Solo se pueden imprimir facturas autorizadas en ARCA"));
        FacturaRestController controlador = new FacturaRestController(servicio, generador);

        assertThrows(IllegalStateException.class, () -> controlador.descargarPdf(9L));
    }

    @Test
    void elNombreDelArchivoLlevaPuntoDeVentaYNumero() {
        assertEquals("factura-C-0003-00000025.pdf",
                FacturaPdfGenerador.nombreArchivo(factura("X", null, "1", new Date())));
    }

    @Test
    void unaFacturaADiscriminaNetoEIva() throws Exception {
        var base = factura("Sofía Indovino", "27381112220", "70000000000002", fecha(2026, 12, 2));
        var facturaA = new FacturaImpresionDTO(base.id(), base.numeroFactura(), base.puntoVentaNumero(),
                base.puntoVentaDescripcion(), base.fechaEmision(), base.clienteDenominacion(),
                base.clienteCuit(), "IVA Responsable Inscripto", 1210.0, base.cae(),
                base.caeFechaVencimiento(), base.detalles(), "A", 1000.0, 210.0);

        String contenido = texto(generador.generar(facturaA));

        assertTrue(contenido.contains("FACTURA A"), contenido);
        assertTrue(contenido.contains("Neto gravado: $1,000.00"), contenido);
        assertTrue(contenido.contains("IVA 21%: $210.00"), contenido);
        assertEquals("factura-A-0003-00000025.pdf", FacturaPdfGenerador.nombreArchivo(facturaA));
    }
}

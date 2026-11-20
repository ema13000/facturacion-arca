package TP.SpringBootGrupal;

import TP.SpringBootGrupal.dtos.Factura.FacturaReporteDTO;
import TP.SpringBootGrupal.service.ReporteFacturas;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.sql.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReporteFacturasTests {

    private FacturaReporteDTO factura(long numero, String cliente, double total, String estado) {
        var factura = new FacturaReporteDTO();
        factura.setNumeroFactura(numero);
        factura.setFechaEmision(Date.valueOf("2026-04-10"));
        factura.setClienteDenominacion(cliente);
        factura.setCondicionIva("Responsable Monotributo");
        factura.setPuntoVentaDescripcion("Sucursal Los Álamos");
        factura.setCantidadItems(2);
        factura.setImporteTotal(total);
        factura.setEstado(estado);
        factura.setCodigoMoneda("PES");
        return factura;
    }

    @Test
    void exportaLasFilasYCalculaElTotalFacturadoSinSumarPendientesNiAnuladas() throws Exception {
        var filas = List.of(
                factura(2, "Juan Pérez", 1149500, "AUTORIZADA"),
                factura(1, "Facultad Regional", 272306, "ANULADA"),
                factura(3, "Librería Centro", 200, "PENDIENTE")
        );
        byte[] contenido = ReporteFacturas.pdf(filas);
        var lector = new PdfReader(contenido);
        try {
            String texto = new PdfTextExtractor(lector).getTextFromPage(1);
            assertTrue(texto.contains("Reporte ejecutivo de ventas"));
            assertTrue(texto.contains("Juan Pérez"));
            assertTrue(texto.contains("Facultad Regional"));
            assertTrue(texto.contains("Total facturado PES: 1149500.00"));
            assertTrue(lector.getPageSizeWithRotation(1).getWidth() > lector.getPageSizeWithRotation(1).getHeight());
        } finally {
            lector.close();
        }

        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(ReporteFacturas.excel(filas)))) {
            var hoja = libro.getSheet("Facturas");
            assertEquals("Moneda", hoja.getRow(0).getCell(8).getStringCellValue());
            assertEquals("Juan Pérez", hoja.getRow(1).getCell(2).getStringCellValue());
            assertEquals("Facultad Regional", hoja.getRow(2).getCell(2).getStringCellValue());
            assertEquals(1149500.0, hoja.getRow(1).getCell(6).getNumericCellValue());
            assertEquals("Total facturado", hoja.getRow(5).getCell(5).getStringCellValue());
            assertEquals(1149500.0, hoja.getRow(5).getCell(6).getNumericCellValue());
        }
    }

    @Test
    void exportaUnReporteVacioCuandoElFiltroNoTieneResultados() throws Exception {
        byte[] contenido = ReporteFacturas.pdf(List.of());
        var lector = new PdfReader(contenido);
        try {
            assertTrue(new PdfTextExtractor(lector).getTextFromPage(1).contains("0 comprobantes"));
        } finally {
            lector.close();
        }
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(ReporteFacturas.excel(List.of())))) {
            assertEquals(1, libro.getSheetAt(0).getPhysicalNumberOfRows());
        }
    }
}

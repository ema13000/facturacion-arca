package TP.SpringBootGrupal.service;

import TP.SpringBootGrupal.dtos.Factura.FacturaReporteDTO;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class ReporteFacturas {

    private static final String[] COLUMNAS = {
            "N° interno", "Fecha", "Cliente", "Condición IVA", "Punto de venta", "Ítems", "Total", "Estado", "Moneda"
    };
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color AZUL = new Color(40, 70, 110);
    private static final String TOTAL_FACTURADO = "Total facturado";

    private ReporteFacturas() {
    }

    public static byte[] pdf(List<FacturaReporteDTO> facturas) {
        var salida = new ByteArrayOutputStream();
        var documento = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        PdfWriter.getInstance(documento, salida);
        documento.open();

        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font encabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, Color.WHITE);
        documento.add(new Paragraph("Reporte ejecutivo de ventas",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16)));
        documento.add(new Paragraph("Generado el " + FECHA.format(LocalDate.now(FacturaServiceImpl.ZONA_ARGENTINA))
                + " · " + facturas.size() + " comprobantes", normal));

        PdfPTable tabla = new PdfPTable(new float[]{12, 15, 35, 30, 25, 10, 18, 15, 12});
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(16);
        tabla.setHeaderRows(1);
        for (String columna : COLUMNAS) {
            var celda = celdaPdf(columna, encabezado);
            celda.setBackgroundColor(AZUL);
            tabla.addCell(celda);
        }

        for (FacturaReporteDTO factura : facturas) {
            String[] valores = {
                    Objects.toString(factura.getNumeroFactura(), ""),
                    FECHA.format(fechaEmision(factura)),
                    factura.getClienteDenominacion(),
                    factura.getCondicionIva(),
                    factura.getPuntoVentaDescripcion(),
                    Long.toString(factura.getCantidadItems()),
                    importe(factura.getImporteTotal()),
                    factura.getEstado(),
                    factura.getCodigoMoneda()
            };
            for (int i = 0; i < valores.length; i++) {
                var celda = celdaPdf(valores[i], normal);
                if (i == 0 || i == 5 || i == 6) {
                    celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                tabla.addCell(celda);
            }
        }
        documento.add(tabla);

        for (var total : totales(facturas).entrySet()) {
            var linea = new Paragraph(TOTAL_FACTURADO + " " + total.getKey() + ": " + total.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
            linea.setAlignment(Element.ALIGN_RIGHT);
            linea.setSpacingBefore(12);
            documento.add(linea);
        }

        documento.close();
        return salida.toByteArray();
    }

    public static byte[] excel(List<FacturaReporteDTO> facturas) {
        try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Facturas");
            CellStyle negrita = libro.createCellStyle();
            var fuente = libro.createFont();
            fuente.setBold(true);
            negrita.setFont(fuente);
            CellStyle fecha = libro.createCellStyle();
            fecha.setDataFormat(libro.createDataFormat().getFormat("dd/mm/yyyy"));
            CellStyle moneda = libro.createCellStyle();
            moneda.setDataFormat(libro.createDataFormat().getFormat("#,##0.00"));
            int[] anchos = {12, 13, 32, 29, 27, 8, 15, 15, 11};

            var cabecera = hoja.createRow(0);
            for (int i = 0; i < COLUMNAS.length; i++) {
                texto(cabecera, i, COLUMNAS[i]);
                cabecera.getCell(i).setCellStyle(negrita);
                hoja.setColumnWidth(i, anchos[i] * 256);
            }

            for (int i = 0; i < facturas.size(); i++) {
                var factura = facturas.get(i);
                var fila = hoja.createRow(i + 1);
                if (factura.getNumeroFactura() != null) {
                    fila.createCell(0).setCellValue(factura.getNumeroFactura());
                }
                var dia = fila.createCell(1);
                dia.setCellValue(fechaEmision(factura));
                dia.setCellStyle(fecha);
                texto(fila, 2, factura.getClienteDenominacion());
                texto(fila, 3, factura.getCondicionIva());
                texto(fila, 4, factura.getPuntoVentaDescripcion());
                fila.createCell(5).setCellValue(factura.getCantidadItems());
                var total = fila.createCell(6);
                total.setCellValue(factura.getImporteTotal());
                total.setCellStyle(moneda);
                texto(fila, 7, factura.getEstado());
                texto(fila, 8, factura.getCodigoMoneda());
            }

            int ultimaFila = facturas.size() + 1;
            int filaTotales = ultimaFila + 1;
            for (String codigo : totales(facturas).keySet()) {
                var fila = hoja.createRow(filaTotales++);
                texto(fila, 5, TOTAL_FACTURADO);
                fila.getCell(5).setCellStyle(negrita);
                texto(fila, 8, codigo);
                var total = fila.createCell(6);
                total.setCellStyle(moneda);
                String rangos = "G2:G" + ultimaFila + ",H2:H" + ultimaFila;
                String criterioMoneda = ",I2:I" + ultimaFila + ",I" + (fila.getRowNum() + 1) + ")";
                total.setCellFormula("SUMIFS(" + rangos + ",\"AUTORIZADA\"" + criterioMoneda
                        + "+SUMIFS(" + rangos + ",\"EMITIDA\"" + criterioMoneda);
            }

            libro.getCreationHelper().createFormulaEvaluator().evaluateAll();
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo generar el reporte Excel", error);
        }
    }

    private static PdfPCell celdaPdf(String texto, Font fuente) {
        var celda = new PdfPCell(new Phrase(Objects.toString(texto, ""), fuente));
        celda.setPadding(5);
        celda.setBorderColor(new Color(213, 220, 231));
        return celda;
    }

    private static void texto(Row fila, int columna, String valor) {
        fila.createCell(columna).setCellValue(Objects.toString(valor, ""));
    }

    private static LocalDate fechaEmision(FacturaReporteDTO factura) {
        return new java.sql.Date(factura.getFechaEmision().getTime()).toLocalDate();
    }

    private static String importe(double total) {
        return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static Map<String, BigDecimal> totales(List<FacturaReporteDTO> facturas) {
        Map<String, BigDecimal> totales = new TreeMap<>();
        for (FacturaReporteDTO factura : facturas) {
            String codigo = Objects.toString(factura.getCodigoMoneda(), "");
            boolean facturada = "AUTORIZADA".equals(factura.getEstado()) || "EMITIDA".equals(factura.getEstado());
            BigDecimal subtotal = facturada ? BigDecimal.valueOf(factura.getImporteTotal()) : BigDecimal.ZERO;
            totales.merge(codigo, subtotal, BigDecimal::add);
        }
        return totales;
    }
}

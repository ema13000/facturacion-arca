package TP.SpringBootGrupal.service;

import TP.SpringBootGrupal.dtos.Factura.FacturaImpresionDTO;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Arma el PDF de una factura C autorizada, con su CAE y la fecha de vencimiento del CAE. */
@Component
public class FacturaPdfGenerador {

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Color GRIS = new Color(238, 241, 246);

    public static String numeroComprobante(FacturaImpresionDTO factura) {
        return String.format("%04d-%08d", factura.puntoVentaNumero(), factura.numeroFactura());
    }

    public static String nombreArchivo(FacturaImpresionDTO factura) {
        return "factura-" + TipoComprobante.normalizar(factura.tipoComprobante()) + "-"
                + numeroComprobante(factura) + ".pdf";
    }

    public byte[] generar(FacturaImpresionDTO factura) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(documento, salida);
        documento.open();

        String letra = TipoComprobante.normalizar(factura.tipoComprobante());
        documento.add(new Paragraph("FACTURA " + letra, TITULO));
        documento.add(new Paragraph("N° " + numeroComprobante(factura), NEGRITA));
        documento.add(new Paragraph("Punto de venta: " + factura.puntoVentaDescripcion(), NORMAL));
        documento.add(new Paragraph("Fecha de emisión: " + fecha(factura.fechaEmision()), NORMAL));
        documento.add(Chunk.NEWLINE);

        documento.add(new Paragraph("Cliente: " + factura.clienteDenominacion(), NEGRITA));
        if (factura.clienteCuit() != null && !factura.clienteCuit().isBlank()) {
            documento.add(new Paragraph("CUIT: " + factura.clienteCuit(), NORMAL));
        }
        documento.add(new Paragraph("Condición frente al IVA: " + factura.condicionIva(), NORMAL));
        documento.add(Chunk.NEWLINE);

        documento.add(tablaDetalles(factura));

        if ("A".equals(letra)) {
            agregarDerecha(documento, "Neto gravado: " + moneda(factura.importeNeto()));
            agregarDerecha(documento, "IVA 21%: " + moneda(factura.importeIva()));
        } else if ("B".equals(letra)) {
            agregarDerecha(documento, "IVA 21% incluido: " + moneda(factura.importeIva()));
        }

        Paragraph total = new Paragraph("TOTAL: " + moneda(factura.importeTotal()), TOTAL);
        total.setAlignment(Element.ALIGN_RIGHT);
        total.setSpacingBefore(10);
        documento.add(total);
        documento.add(Chunk.NEWLINE);

        if (factura.observaciones() != null && !factura.observaciones().isBlank()) {
            documento.add(new Paragraph("Observaciones", NEGRITA));
            documento.add(new Paragraph(factura.observaciones(), NORMAL));
            documento.add(Chunk.NEWLINE);
        }

        PdfPTable cae = new PdfPTable(1);
        cae.setWidthPercentage(100);
        PdfPCell celda = new PdfPCell();
        celda.setBackgroundColor(GRIS);
        celda.setPadding(10);
        celda.addElement(new Paragraph("CAE N°: " + factura.cae(), NEGRITA));
        celda.addElement(new Paragraph("Fecha de vencimiento del CAE: "
                + fecha(factura.caeFechaVencimiento()), NEGRITA));
        celda.addElement(new Paragraph("Comprobante autorizado por ARCA", NORMAL));
        cae.addCell(celda);
        documento.add(cae);

        documento.close();
        return salida.toByteArray();
    }

    private void agregarDerecha(Document documento, String texto) {
        Paragraph parrafo = new Paragraph(texto, NORMAL);
        parrafo.setAlignment(Element.ALIGN_RIGHT);
        documento.add(parrafo);
    }

    private PdfPTable tablaDetalles(FacturaImpresionDTO factura) {
        PdfPTable tabla = new PdfPTable(new float[]{4, 1.2f, 1.8f, 1.2f, 1.8f});
        tabla.setWidthPercentage(100);
        for (String titulo : new String[]{"Descripción", "Cantidad", "Precio unit.", "Bonif. %", "Subtotal"}) {
            PdfPCell encabezado = new PdfPCell(new Phrase(titulo, NEGRITA));
            encabezado.setBackgroundColor(GRIS);
            encabezado.setPadding(5);
            tabla.addCell(encabezado);
        }
        for (FacturaImpresionDTO.Renglon renglon : factura.detalles()) {
            tabla.addCell(celda(renglon.descripcion(), Element.ALIGN_LEFT));
            tabla.addCell(celda(cantidad(renglon.cantidad()), Element.ALIGN_RIGHT));
            tabla.addCell(celda(moneda(renglon.precioUnitario()), Element.ALIGN_RIGHT));
            tabla.addCell(celda(cantidad(renglon.porcentajeBonificacion()), Element.ALIGN_RIGHT));
            tabla.addCell(celda(moneda(renglon.importeSubtotal()), Element.ALIGN_RIGHT));
        }
        return tabla;
    }

    private PdfPCell celda(String texto, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, NORMAL));
        celda.setHorizontalAlignment(alineacion);
        celda.setPadding(5);
        return celda;
    }

    private String fecha(Date fecha) {
        return fecha == null ? "-" : new SimpleDateFormat("dd/MM/yyyy").format(fecha);
    }

    private String moneda(double importe) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(importe);
    }

    private String cantidad(double valor) {
        return valor == Math.floor(valor) ? String.valueOf((long) valor) : String.valueOf(valor);
    }
}

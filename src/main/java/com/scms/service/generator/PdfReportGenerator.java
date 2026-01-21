package com.scms.service.generator;

import com.scms.service.dto.InventoryRow;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PdfReportGenerator {

    public void createMonthlyReport(OutputStream out, int year, int month, List<InventoryRow> rows) throws IOException {
        String title = String.format("Prometi kartica robnog knjigovodstva - %02d.%d", month, year);
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        createReportDocument(out, title, from, to, rows, false);
    }

    public void createYearlyReport(OutputStream out, int year, List<InventoryRow> rows) throws IOException {
        String title = String.format("Godišnji izvještaj - %d", year);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        createReportDocument(out, title, from, to, rows, false);
    }

    // Deliveries (product exports) variants
    public void createMonthlyDeliveriesReport(OutputStream out, int year, int month, List<InventoryRow> rows) throws IOException {
        String title = String.format("Dostave - %02d.%d", month, year);
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        createReportDocument(out, title, from, to, rows, true);
    }

    public void createYearlyDeliveriesReport(OutputStream out, int year, List<InventoryRow> rows) throws IOException {
        String title = String.format("Dostave - %d", year);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        createReportDocument(out, title, from, to, rows, true);
    }

    /**
     * core: isDeliveries=true => deliveries layout (omit Ulaz(vrijednost), include Izlaz(vrijednost))
     */
    private void createReportDocument(OutputStream out, String title, LocalDate from, LocalDate to, List<InventoryRow> rows, boolean isDeliveries) throws IOException {
        Document doc = new Document(PageSize.A4, 36, 36, 64, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            HeaderFooterPageEvent event = new HeaderFooterPageEvent(title, from, to);
            writer.setPageEvent(event);

            doc.open();

            // Company header
            Font companyFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            Paragraph comp = new Paragraph("Seherconi d.o.o.", companyFont);
            comp.setAlignment(Element.ALIGN_LEFT);
            doc.add(comp);
            Font addrFont = new Font(Font.HELVETICA, 9);
            Paragraph addr = new Paragraph("Adresa: Ulica 123, Grad", addrFont);
            addr.setSpacingAfter(8);
            doc.add(addr);

            // Title and period (already drawn in header by HeaderFooterPageEvent, but keep center title as well)
            Font titleFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            Paragraph pTitle = new Paragraph(title, titleFont);
            pTitle.setAlignment(Element.ALIGN_CENTER);
            pTitle.setSpacingBefore(4);
            pTitle.setSpacingAfter(4);
            doc.add(pTitle);

            Paragraph period = new Paragraph("Period: " + from.format(DateTimeFormatter.ISO_DATE) + " - " + to.format(DateTimeFormatter.ISO_DATE), addrFont);
            period.setAlignment(Element.ALIGN_CENTER);
            period.setSpacingAfter(10);
            doc.add(period);

            // Prepare table columns depending on type
            PdfPTable table;
            if (isDeliveries) {
                // columns: Šifra | Naziv | Jedinica | Ulaz(kol) | Izlaz(kol) | Izlaz(vrijednost) | Stanje
                table = new PdfPTable(new float[]{1.4f, 3.0f, 1.2f, 1.2f, 1.2f, 1.6f, 1.6f});
            } else {
                // materials: Šifra | Naziv | Jedinica | Ulaz(kol) | Izlaz(kol) | Ulaz(vrijednost) | Stanje
                table = new PdfPTable(new float[]{1.4f, 3.0f, 1.2f, 1.2f, 1.2f, 1.6f, 1.6f});
            }
            table.setWidthPercentage(100);
            addTableHeader(table, isDeliveries);

            Font cellFont = new Font(Font.HELVETICA, 9);
            for (InventoryRow r : rows) {
                table.addCell(makeCell(r.getMaterialCode(), cellFont));
                table.addCell(makeCell(r.getName(), cellFont));
                table.addCell(makeCell(r.getUnit(), cellFont));
                table.addCell(makeCell(formatDouble(r.getInflow()), cellFont, Element.ALIGN_RIGHT));
                table.addCell(makeCell(formatDouble(r.getOutflow()), cellFont, Element.ALIGN_RIGHT));
                if (isDeliveries) {
                    table.addCell(makeCell(formatDouble(r.getOutflowValue()), cellFont, Element.ALIGN_RIGHT));
                } else {
                    table.addCell(makeCell(formatDouble(r.getInflowValue()), cellFont, Element.ALIGN_RIGHT));
                }
                table.addCell(makeCell(formatDouble(r.getBalance()), cellFont, Element.ALIGN_RIGHT));
            }

            // Totals row
            PdfPCell totalLabel = new PdfPCell(new Phrase("UKUPNO", new Font(Font.HELVETICA, 10, Font.BOLD)));
            totalLabel.setColspan(3);
            totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabel.setPadding(6);
            table.addCell(totalLabel);

            double tIn = rows.stream().mapToDouble(InventoryRow::getInflow).sum();
            double tOut = rows.stream().mapToDouble(InventoryRow::getOutflow).sum();
            double tVal;
            if (isDeliveries) tVal = rows.stream().mapToDouble(InventoryRow::getOutflowValue).sum();
            else tVal = rows.stream().mapToDouble(InventoryRow::getInflowValue).sum();
            double tBal = rows.stream().mapToDouble(InventoryRow::getBalance).sum();

            table.addCell(makeCell(formatDouble(tIn), cellFont, Element.ALIGN_RIGHT));
            table.addCell(makeCell(formatDouble(tOut), cellFont, Element.ALIGN_RIGHT));
            table.addCell(makeCell(formatDouble(tVal), cellFont, Element.ALIGN_RIGHT));
            table.addCell(makeCell(formatDouble(tBal), cellFont, Element.ALIGN_RIGHT));

            doc.add(table);

            doc.close();
        } catch (DocumentException ex) {
            throw new IOException(ex);
        }
    }

    private PdfPCell makeCell(String text, Font font) {
        return makeCell(text, font, Element.ALIGN_LEFT);
    }

    private PdfPCell makeCell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setPadding(6);
        c.setHorizontalAlignment(align);
        return c;
    }

    private void addTableHeader(PdfPTable table, boolean isDeliveries) {
        Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);
        table.addCell(new PdfPCell(new Phrase("Šifra / Artikl", bold)));
        table.addCell(new PdfPCell(new Phrase("Naziv", bold)));
        table.addCell(new PdfPCell(new Phrase("Jedinica mjere", bold)));
        table.addCell(new PdfPCell(new Phrase("Ulaz (količina)", bold)));
        table.addCell(new PdfPCell(new Phrase("Izlaz (količina)", bold)));
        if (isDeliveries) {
            table.addCell(new PdfPCell(new Phrase("Izlaz (vrijednost)", bold)));
        } else {
            table.addCell(new PdfPCell(new Phrase("Ulaz (vrijednost)", bold)));
        }
        table.addCell(new PdfPCell(new Phrase("Stanje", bold)));
    }

    private String formatDouble(double v) { return String.format("%,.2f", v); }

    // Page event for header/footer
    static class HeaderFooterPageEvent extends PdfPageEventHelper {
        private final String title;
        private final LocalDate from;
        private final LocalDate to;
        private final Font footerFont = new Font(Font.HELVETICA, 8);

        public HeaderFooterPageEvent(String title, LocalDate from, LocalDate to) {
            this.title = title; this.from = from; this.to = to;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            // page number
            String page = "Stranica " + writer.getPageNumber();
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT, new Phrase(page, footerFont), document.right() - 36, document.bottom() - 20, 0);
            // print date/time
            String printed = "Ispisano: " + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(printed, footerFont), document.left() + 36, document.bottom() - 20, 0);
            // header: title and period
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            String header = title + " — " + from.format(DateTimeFormatter.ISO_DATE) + " - " + to.format(DateTimeFormatter.ISO_DATE);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, new Phrase(header, headerFont), (document.right() + document.left())/2, document.top() + 10, 0);
        }
    }
}

package com.scms.service.generator;

import com.scms.service.dto.InventoryRow;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class CsvExporter {

    // Materials CSV: includes Ulaz(količina), Izlaz(količina), Ulaz(vrijednost), Stanje
    public void exportInventoryCsv(Writer writer, List<InventoryRow> rows) throws IOException {
        String[] headers = new String[]{"Šifra / Artikl","Naziv","Jedinica mjere","Ulaz (količina)","Izlaz (količina)","Ulaz (vrijednost)","Stanje"};
        writeRow(writer, headers);
        for (InventoryRow r : rows) {
            writeRow(writer, new String[]{
                    safe(r.getMaterialCode()),
                    safe(r.getName()),
                    safe(r.getUnit()),
                    fmtDouble(r.getInflow()),
                    fmtDouble(r.getOutflow()),
                    fmtDouble(r.getInflowValue()),
                    fmtDouble(r.getBalance())
            });
        }
        writer.flush();
    }

    // Deliveries CSV: typically for products - include Izlaz (vrijednost) and omit Ulaz (vrijednost)
    public void exportDeliveriesCsv(Writer writer, List<InventoryRow> rows) throws IOException {
        String[] headers = new String[]{"Šifra / Artikl","Naziv","Jedinica mjere","Ulaz (količina)","Izlaz (količina)","Izlaz (vrijednost)","Stanje"};
        writeRow(writer, headers);
        for (InventoryRow r : rows) {
            writeRow(writer, new String[]{
                    safe(r.getMaterialCode()),
                    safe(r.getName()),
                    safe(r.getUnit()),
                    fmtDouble(r.getInflow()),
                    fmtDouble(r.getOutflow()),
                    fmtDouble(r.getOutflowValue()),
                    fmtDouble(r.getBalance())
            });
        }
        writer.flush();
    }

    private void writeRow(Writer w, String[] cols) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cols[i]));
        }
        sb.append('\n');
        w.write(sb.toString());
    }

    private String escape(String s) {
        if (s == null) return "";
        boolean needQuotes = s.contains(",") || s.contains("\n") || s.contains("\"");
        String out = s.replace("\"", "\"\"");
        if (needQuotes) return '"' + out + '"';
        return out;
    }

    private String safe(String s) { return s == null ? "" : s; }
    private String fmtDouble(double v) { return String.format("%,.2f", v); }
}

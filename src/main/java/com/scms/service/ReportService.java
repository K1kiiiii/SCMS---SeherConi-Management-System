package com.scms.service;

import com.scms.service.dto.InventoryRow;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportService {

    // instantiate generators via fully-qualified names to avoid import resolution issues in some build environments
    private final com.scms.service.generator.PdfReportGenerator pdfGen = new com.scms.service.generator.PdfReportGenerator();
    private final com.scms.service.generator.CsvExporter csvExporter = new com.scms.service.generator.CsvExporter();
    private final com.scms.service.generator.LabelGenerator labelGen = new com.scms.service.generator.LabelGenerator();

    /**
     * Aggregate inventory rows for materials between given dates (inclusive).
     * Uses inventory_movements table as single source of truth for inflows/outflows and monetary values.
     */
    public List<InventoryRow> aggregateForPeriod(LocalDate from, LocalDate to) throws SQLException {
        List<InventoryRow> rows = new ArrayList<>();
        com.scms.dao.MaterialDao materialDao = new com.scms.dao.MaterialDao();
        com.scms.dao.InventoryMovementDao imDao = new com.scms.dao.InventoryMovementDao();

        // time window
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);

        // load all movements and filter by range (InventoryMovementDao doesn't provide aggregated query)
        List<com.scms.model.InventoryMovement> movements;
        try {
            movements = imDao.findAll();
        } catch (Exception ex) {
            throw new SQLException("Failed to load inventory movements: " + ex.getMessage(), ex);
        }

        // aggregate by material id
        Map<Integer, InventoryRow> agg = new HashMap<>();

        // If there are no movements at all (legacy DB), fallback to assignments aggregation
        boolean usedFallback = false;
        if (movements == null || movements.isEmpty()) {
            usedFallback = true;
            try {
                com.scms.dao.AssignmentDao assignmentDao = new com.scms.dao.AssignmentDao();
                List<com.scms.model.Assignment> assigns = assignmentDao.findAll();
                for (com.scms.model.Assignment a : assigns) {
                    if (a.getAssignedAt() == null) continue;
                    if (a.getAssignedAt().isBefore(start) || a.getAssignedAt().isAfter(end)) continue;
                    int mid = a.getMaterialId();
                    InventoryRow r = agg.computeIfAbsent(mid, k -> {
                        InventoryRow ir = new InventoryRow();
                        ir.setMaterialId(k);
                        ir.setMaterialCode(String.valueOf(k));
                        ir.setName("");
                        ir.setUnit("");
                        ir.setInflow(0.0);
                        ir.setOutflow(0.0);
                        ir.setInflowValue(0.0);
                        ir.setOutflowValue(0.0);
                        ir.setBalance(0.0);
                        return ir;
                    });
                    r.setOutflow(r.getOutflow() + a.getQuantity());
                    // no monetary info in Assignment, leave outflowValue as 0
                }
            } catch (Exception ex) {
                // ignore fallback errors; we'll still return rows with zeros
            }
        }

        if (!usedFallback) {
            for (com.scms.model.InventoryMovement mv : movements) {
                 if (mv.getCreatedAt() == null) continue;
                 if (mv.getCreatedAt().isBefore(start) || mv.getCreatedAt().isAfter(end)) continue;
                 Integer mid = mv.getMaterialId();
                 if (mid == null) continue; // skip product movements
                 InventoryRow r = agg.computeIfAbsent(mid, k -> {
                     InventoryRow ir = new InventoryRow();
                     ir.setMaterialId(k);
                     ir.setMaterialCode(String.valueOf(k));
                     ir.setName("");
                     ir.setUnit("");
                     ir.setInflow(0.0);
                     ir.setOutflow(0.0);
                     ir.setInflowValue(0.0);
                     ir.setOutflowValue(0.0);
                     ir.setBalance(0.0);
                     return ir;
                 });

                 double qty = mv.getQuantity() == null ? 0.0 : mv.getQuantity();
                 Double totalPrice = mv.getTotalPrice();
                 Double unitPrice = mv.getUnitPrice();
                 double value = 0.0;
                 if (totalPrice != null) value = totalPrice;
                 else if (unitPrice != null) value = unitPrice * qty;

                 if (mv.getType() != null && mv.getType().equalsIgnoreCase("IN")) {
                     r.setInflow(r.getInflow() + qty);
                     r.setInflowValue(r.getInflowValue() + value);
                 } else if (mv.getType() != null && mv.getType().equalsIgnoreCase("OUT")) {
                     r.setOutflow(r.getOutflow() + qty);
                     r.setOutflowValue(r.getOutflowValue() + value);
                 }
             }
        } // end usedFallback check

        // now ensure every material is represented and fill names and balance
        List<com.scms.model.Material> mats = materialDao.findAll();
        for (com.scms.model.Material m : mats) {
            InventoryRow r = agg.getOrDefault(m.getId(), new InventoryRow());
            r.setMaterialId(m.getId());
            // no separate "code" field in Material, use id as code
            r.setMaterialCode(String.valueOf(m.getId()));
            r.setName(m.getName());
            r.setUnit(m.getUnit());
            // balance = current quantity in materials table
            r.setBalance(m.getQuantity());
            // fields initialized to 0.0 when created, so no additional fixes required
            rows.add(r);
        }

        return rows;
    }

    /**
     * Aggregate deliveries (product exports) grouped by product_id between dates.
     * Returns InventoryRow list where materialId field holds productId and name is product name.
     */
    public List<InventoryRow> aggregateDeliveriesForPeriod(LocalDate from, LocalDate to) throws SQLException {
        List<InventoryRow> rows = new ArrayList<>();
        com.scms.dao.ProductDao productDao = new com.scms.dao.ProductDao();
        com.scms.dao.InventoryMovementDao imDao = new com.scms.dao.InventoryMovementDao();

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.atTime(LocalTime.MAX);

        List<com.scms.model.InventoryMovement> movements;
        try {
            movements = imDao.findAll();
        } catch (Exception ex) {
            throw new SQLException("Failed to load inventory movements: " + ex.getMessage(), ex);
        }

        Map<Integer, InventoryRow> agg = new HashMap<>();
        for (com.scms.model.InventoryMovement mv : movements) {
            if (mv.getCreatedAt() == null) continue;
            if (mv.getCreatedAt().isBefore(start) || mv.getCreatedAt().isAfter(end)) continue;
            Integer pid = mv.getProductId();
            if (pid == null) continue;

            InventoryRow r = agg.computeIfAbsent(pid, k -> {
                InventoryRow ir = new InventoryRow();
                ir.setMaterialId(k);
                ir.setMaterialCode(String.valueOf(k));
                ir.setName("");
                ir.setUnit("kutije");
                ir.setInflow(0.0);
                ir.setOutflow(0.0);
                ir.setInflowValue(0.0);
                ir.setOutflowValue(0.0);
                ir.setBalance(0.0);
                return ir;
            });

            double qty = mv.getQuantity() == null ? 0.0 : mv.getQuantity();
            Double totalPrice = mv.getTotalPrice();
            Double unitPrice = mv.getUnitPrice();
            double value = 0.0;
            if (totalPrice != null) value = totalPrice;
            else if (unitPrice != null) value = unitPrice * qty;

            if (mv.getType() != null && mv.getType().equalsIgnoreCase("OUT")) {
                r.setOutflow(r.getOutflow() + qty);
                r.setOutflowValue(r.getOutflowValue() + value);
            } else if (mv.getType() != null && mv.getType().equalsIgnoreCase("IN")) {
                r.setInflow(r.getInflow() + qty);
                r.setInflowValue(r.getInflowValue() + value);
            }
        }

        // fill product names and optional balance from product table
        List<com.scms.model.Product> prods = productDao.findAll();
        for (com.scms.model.Product p : prods) {
            InventoryRow r = agg.getOrDefault(p.getId(), new InventoryRow());
            r.setMaterialId(p.getId());
            r.setMaterialCode(String.valueOf(p.getId()));
            r.setName(p.getName());
            r.setUnit("kutije");
            // balance: current quantityBoxes
            r.setBalance(p.getQuantityBoxes());
            // fields initialized earlier; keep as-is
            rows.add(r);
        }

        return rows;
    }

    public Path generateMonthlyPdf(int year, int month, Path outFile) throws IOException, SQLException {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        List<InventoryRow> rows = aggregateForPeriod(from, to);
        try (FileOutputStream fos = new FileOutputStream(outFile.toFile())) {
            pdfGen.createMonthlyReport(fos, year, month, rows);
        }
        return outFile;
    }

    public Path generateYearlyPdf(int year, Path outFile) throws IOException, SQLException {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        List<InventoryRow> rows = aggregateForPeriod(from, to);
        try (FileOutputStream fos = new FileOutputStream(outFile.toFile())) {
            pdfGen.createYearlyReport(fos, year, rows);
        }
        return outFile;
    }

    public Path exportCsvForPeriod(LocalDate from, LocalDate to, Path outFile) throws IOException, SQLException {
        List<InventoryRow> rows = aggregateForPeriod(from, to);
        try (java.io.Writer w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            csvExporter.exportInventoryCsv(w, rows);
        }
        return outFile;
    }

    // deliveries/pdf for products
    public Path generateMonthlyDeliveriesPdf(int year, int month, Path outFile) throws IOException, SQLException {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        List<InventoryRow> rows = aggregateDeliveriesForPeriod(from, to);
        try (FileOutputStream fos = new FileOutputStream(outFile.toFile())) {
            pdfGen.createMonthlyDeliveriesReport(fos, year, month, rows);
        }
        return outFile;
    }

    public Path generateYearlyDeliveriesPdf(int year, Path outFile) throws IOException, SQLException {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        List<InventoryRow> rows = aggregateDeliveriesForPeriod(from, to);
        try (FileOutputStream fos = new FileOutputStream(outFile.toFile())) {
            pdfGen.createYearlyDeliveriesReport(fos, year, rows);
        }
        return outFile;
    }

    public Path exportDeliveriesCsvForPeriod(LocalDate from, LocalDate to, Path outFile) throws IOException, SQLException {
        List<InventoryRow> rows = aggregateDeliveriesForPeriod(from, to);
        try (java.io.Writer w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            csvExporter.exportDeliveriesCsv(w, rows);
        }
        return outFile;
    }

    public Path generateRecipeLabel(int recipeId, int quantity, Path outFile) throws Exception {
        com.scms.dao.RecipeDao rd = new com.scms.dao.RecipeDao();
        com.scms.model.Recipe r = rd.findById(recipeId).orElseThrow(() -> new Exception("Recipe not found"));
        try (FileOutputStream fos = new FileOutputStream(outFile.toFile())) {
            labelGen.createRecipeLabel(fos, r, quantity);
        }
        return outFile;
    }
}

package com.scms.service;

import com.scms.service.dto.InventoryRow;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportService {

    // instantiate generators via fully-qualified names to avoid import resolution issues in some build environments
    private final com.scms.service.generator.PdfReportGenerator pdfGen = new com.scms.service.generator.PdfReportGenerator();
    private final com.scms.service.generator.CsvExporter csvExporter = new com.scms.service.generator.CsvExporter();
    private final com.scms.service.generator.LabelGenerator labelGen = new com.scms.service.generator.LabelGenerator();

    // For now, aggregate data by querying existing DAOs directly. We'll create a very simple aggregation
    // that sums assignments (outflow) and tasks/recipes (consumption) as outflow, and recipe production + manual inflow as inflow.

    public List<InventoryRow> aggregateForPeriod(LocalDate from, LocalDate to) throws SQLException {
        // Basic implementation: load materials and compute inflow/outflow from assignments and tasks
        // This project doesn't have a dedicated inventory_movements table, so infer from assignments (out) and materials updates (in)
        List<InventoryRow> rows = new ArrayList<>();
        // Use MaterialDao to list all materials and assignments to sum quantities in period
        com.scms.dao.MaterialDao materialDao = new com.scms.dao.MaterialDao();
        com.scms.dao.AssignmentDao assignmentDao = new com.scms.dao.AssignmentDao();
        com.scms.dao.TaskDao taskDao = new com.scms.dao.TaskDao();

        try {
            for (com.scms.model.Material m : materialDao.findAll()) {
                InventoryRow r = new InventoryRow();
                r.setMaterialId(m.getId());
                r.setMaterialCode(String.valueOf(m.getId()));
                r.setName(m.getName());
                r.setUnit(m.getUnit());

                // sum outflows from assignments within period
                double outflow = 0.0;
                for (com.scms.model.Assignment a : assignmentDao.findAll()) {
                    if (a.getMaterialId() == m.getId() && a.getAssignedAt() != null) {
                        LocalDate at = a.getAssignedAt().toLocalDate();
                        if ((at.isEqual(from) || at.isAfter(from)) && (at.isEqual(to) || at.isBefore(to))) {
                            outflow += a.getQuantity();
                        }
                    }
                }

                // tasks produced may represent consumption of materials via recipe items; Tasks may have producedQuantity and completedAt
                double taskOut = 0.0;
                for (com.scms.model.Task t : taskDao.findAll()) {
                    if (t.getCompletedAt() != null) {
                        LocalDate d = t.getCompletedAt().toLocalDate();
                        if ((d.isEqual(from) || d.isAfter(from)) && (d.isEqual(to) || d.isBefore(to))) {
                            // we would need to expand recipe items to compute consumption; skip for now
                        }
                    }
                }

                r.setOutflow(outflow + taskOut);
                // inflow is not tracked here (receipts). We'll approximate inflow = 0 and set balance to current material quantity
                r.setInflow(0.0);
                r.setBalance(m.getQuantity());
                rows.add(r);
            }
        } catch (SQLException ex) {
            throw ex;
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

    public Path generateRecipeLabel(int recipeId, int quantity, Path outFile) throws Exception {
        com.scms.dao.RecipeDao rd = new com.scms.dao.RecipeDao();
        com.scms.model.Recipe r = rd.findById(recipeId).orElseThrow(() -> new Exception("Recipe not found"));
        try (FileOutputStream fos = new FileOutputStream(outFile.toFile())) {
            labelGen.createRecipeLabel(fos, r, quantity);
        }
        return outFile;
    }
}

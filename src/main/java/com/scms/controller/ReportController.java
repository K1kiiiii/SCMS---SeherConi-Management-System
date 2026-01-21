package com.scms.controller;

import com.scms.service.ReportService;
import com.scms.service.generator.LabelGenerator;
import com.scms.util.DialogUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;

public class ReportController {

    @FXML private ComboBox<Integer> cbMonth;
    @FXML private ComboBox<Integer> cbYear;
    @FXML private ComboBox<Integer> cbYear2;
    @FXML private TextField tfRecipeId;
    @FXML private TextField tfLabelQty;

    private final ReportService reportService = new ReportService();
    private final LabelGenerator labelGenerator = new LabelGenerator();

    private static final LocalDate MIN_DATE = LocalDate.of(2026, 1, 1);

    @FXML
    public void initialize() {
        for (int m = 1; m <= 12; m++) cbMonth.getItems().add(m);
        int nowYear = LocalDate.now().getYear();
        for (int y = nowYear - 5; y <= nowYear + 1; y++) { cbYear.getItems().add(y); cbYear2.getItems().add(y); }
        cbMonth.getSelectionModel().select(LocalDate.now().getMonthValue() - 1);
        cbYear.getSelectionModel().select((Integer)nowYear);
        cbYear2.getSelectionModel().select((Integer)nowYear);
    }

    private boolean validateMonthYear(int year, int month) {
        LocalDate from = YearMonth.of(year, month).atDay(1);
        if (from.isBefore(MIN_DATE)) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Izabrani datum ne može biti prije 01.01.2026.", ButtonType.OK);
            DialogUtils.styleAlert(a);
            a.showAndWait();
            return false;
        }
        return true;
    }

    private boolean validateYear(int year) {
        LocalDate from = LocalDate.of(year, 1, 1);
        if (from.isBefore(MIN_DATE)) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Izabrani datum ne može biti prije 01.01.2026.", ButtonType.OK);
            DialogUtils.styleAlert(a);
            a.showAndWait();
            return false;
        }
        return true;
    }

    @FXML
    public void onGenerateMonthlyPdf(ActionEvent ev) {
        Integer month = cbMonth.getValue(); Integer year = cbYear.getValue();
        if (month == null || year == null) return;
        if (!validateMonthYear(year, month)) return;
        FileChooser fc = new FileChooser(); fc.setInitialFileName(String.format("report_%02d_%d.pdf", month, year));
        File f = fc.showSaveDialog(getWindow());
        if (f == null) return;
        try {
            reportService.generateMonthlyPdf(year, month, f.toPath());
            Alert ok = new Alert(Alert.AlertType.INFORMATION, "PDF uspješno generiran: " + f.getAbsolutePath(), ButtonType.OK);
            DialogUtils.styleAlert(ok);
            ok.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
            Alert err = new Alert(Alert.AlertType.ERROR, "Greška pri generiranju PDF: " + ex.getMessage(), ButtonType.OK);
            DialogUtils.styleAlert(err);
            err.showAndWait();
        }
    }

    @FXML
    public void onGenerateYearlyPdf(ActionEvent ev) {
        Integer year = cbYear2.getValue(); if (year == null) return; if (!validateYear(year)) return;
        FileChooser fc = new FileChooser(); fc.setInitialFileName(String.format("report_%d.pdf", year));
        File f = fc.showSaveDialog(getWindow()); if (f == null) return;
        try {
            reportService.generateYearlyPdf(year, f.toPath());
            Alert ok = new Alert(Alert.AlertType.INFORMATION, "PDF uspješno generiran: " + f.getAbsolutePath(), ButtonType.OK);
            DialogUtils.styleAlert(ok);
            ok.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
            Alert err = new Alert(Alert.AlertType.ERROR, "Greška pri generiranju PDF: " + ex.getMessage(), ButtonType.OK);
            DialogUtils.styleAlert(err);
            err.showAndWait();
        }
    }

    @FXML
    public void onExportCsvForPeriod(ActionEvent ev) {
        // for simplicity use month/year selection
        Integer month = cbMonth.getValue(); Integer year = cbYear.getValue(); if (month == null || year == null) return; if (!validateMonthYear(year, month)) return;
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        FileChooser fc = new FileChooser(); fc.setInitialFileName(String.format("report_%02d_%d.csv", month, year));
        File f = fc.showSaveDialog(getWindow()); if (f == null) return;
        try {
            reportService.exportCsvForPeriod(from, to, f.toPath());
            Alert ok = new Alert(Alert.AlertType.INFORMATION, "CSV uspješno eksportiran: " + f.getAbsolutePath(), ButtonType.OK);
            DialogUtils.styleAlert(ok);
            ok.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
            Alert err = new Alert(Alert.AlertType.ERROR, "Greška pri exportu CSV: " + ex.getMessage(), ButtonType.OK);
            DialogUtils.styleAlert(err);
            err.showAndWait();
        }
    }

    @FXML
    public void onPreviewLabel(ActionEvent ev) {
        try {
            int rid = Integer.parseInt(tfRecipeId.getText());
            int qty = Integer.parseInt(tfLabelQty.getText());
            com.scms.dao.RecipeDao rd = new com.scms.dao.RecipeDao();
            com.scms.model.Recipe r = rd.findById(rid).orElseThrow(() -> new Exception("Recipe not found"));
            String preview = labelGenerator.renderRecipeLabelPreview(r, qty);
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            DialogUtils.styleAlert(a);
            a.setTitle("Preview labele");
            a.setHeaderText("Preview label za recept: " + r.getName());
            TextArea ta = new TextArea(preview);
            ta.setEditable(false);
            ta.setWrapText(true);
            ta.setMaxWidth(Double.MAX_VALUE);
            ta.setMaxHeight(Double.MAX_VALUE);
            a.getDialogPane().setExpandableContent(ta);
            a.getDialogPane().setExpanded(true);
            a.showAndWait();
        } catch (NumberFormatException nfe) {
            Alert w = new Alert(Alert.AlertType.WARNING, "Neispravan ID recepta ili količina", ButtonType.OK);
            DialogUtils.styleAlert(w);
            w.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
            Alert err = new Alert(Alert.AlertType.ERROR, "Greška pri generiranju preview-a: " + ex.getMessage(), ButtonType.OK);
            DialogUtils.styleAlert(err);
            err.showAndWait();
        }
    }

    @FXML
    public void onExportLabelPdf(ActionEvent ev) {
        try {
            int rid = Integer.parseInt(tfRecipeId.getText());
            int qty = Integer.parseInt(tfLabelQty.getText());
            FileChooser fc = new FileChooser(); fc.setInitialFileName(String.format("label_recipe_%d.pdf", rid));
            File f = fc.showSaveDialog(getWindow()); if (f == null) return;
            reportService.generateRecipeLabel(rid, qty, f.toPath());
            Alert ok = new Alert(Alert.AlertType.INFORMATION, "Labela generirana: " + f.getAbsolutePath(), ButtonType.OK);
            DialogUtils.styleAlert(ok);
            ok.showAndWait();
        } catch (NumberFormatException nfe) {
            Alert w = new Alert(Alert.AlertType.WARNING, "Neispravan ID recepta ili količina", ButtonType.OK);
            DialogUtils.styleAlert(w);
            w.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();
            Alert err = new Alert(Alert.AlertType.ERROR, "Greška pri generiranju labela: " + ex.getMessage(), ButtonType.OK);
            DialogUtils.styleAlert(err);
            err.showAndWait();
        }
    }

    @FXML
    public void onClose(ActionEvent ev) { ((javafx.stage.Stage) getWindow()).close(); }

    private Window getWindow() { return cbMonth.getScene().getWindow(); }
}

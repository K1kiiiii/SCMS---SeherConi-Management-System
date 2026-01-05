package com.scms.service.generator;

import com.scms.model.Recipe;
import com.scms.dao.RecipeItemDao;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

public class LabelGenerator {

    public void createRecipeLabel(OutputStream out, Recipe recipe, int quantity) throws IOException {
        // If OpenPDF is available, a better PDF label could be produced. For portability, output a small UTF-8 text label into the stream.
        try (OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write("RECEPT: " + recipe.getName() + "\n");
            w.write("Količina: " + quantity + "\n\n");
            w.write("Sastojci:\n");
            try {
                RecipeItemDao rid = new RecipeItemDao();
                List<com.scms.model.RecipeItem> items = rid.findByRecipeId(recipe.getId());
                for (com.scms.model.RecipeItem it : items) {
                    String line = String.format("- %s %s %s\n", getMaterialName(it.getMaterialId()), it.getQuantity(), it.getUnit());
                    w.write(line);
                }
            } catch (Exception ex) {
                w.write("(nije moguće dohvatiti sastojke)\n");
            }
            w.flush();
        }
    }

    // Return a textual preview of the label (used by the UI preview window)
    public String renderRecipeLabelPreview(Recipe recipe, int quantity) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("RECEPT: ").append(recipe.getName()).append('\n');
        sb.append("Količina: ").append(quantity).append('\n').append('\n');
        sb.append("Sastojci:\n");
        RecipeItemDao rid = new RecipeItemDao();
        List<com.scms.model.RecipeItem> items = rid.findByRecipeId(recipe.getId());
        for (com.scms.model.RecipeItem it : items) {
            String line = String.format("- %s %s %s", getMaterialName(it.getMaterialId()), it.getQuantity(), it.getUnit());
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private String getMaterialName(int materialId) {
        try {
            com.scms.dao.MaterialDao md = new com.scms.dao.MaterialDao();
            return md.findById(materialId).map(m -> m.getName()).orElse("?");
        } catch (Exception ex) {
            return "?";
        }
    }
}

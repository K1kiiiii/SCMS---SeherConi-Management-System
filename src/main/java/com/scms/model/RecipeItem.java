package com.scms.model;

public class RecipeItem {
    private int id;
    private int recipeId;
    private int materialId;
    private double quantity;
    private String unit;

    public RecipeItem() {}

    public RecipeItem(int id, int recipeId, int materialId, double quantity, String unit) {
        this.id = id; this.recipeId = recipeId; this.materialId = materialId; this.quantity = quantity; this.unit = unit;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getRecipeId() { return recipeId; }
    public void setRecipeId(int recipeId) { this.recipeId = recipeId; }

    public int getMaterialId() { return materialId; }
    public void setMaterialId(int materialId) { this.materialId = materialId; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}


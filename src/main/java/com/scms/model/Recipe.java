package com.scms.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Recipe {
    private int id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private List<RecipeItem> items = new ArrayList<>();

    public Recipe() {}

    public Recipe(int id, String name, String description, LocalDateTime createdAt) {
        this.id = id; this.name = name; this.description = description; this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<RecipeItem> getItems() { return items; }
    public void setItems(List<RecipeItem> items) { this.items = items; }
}


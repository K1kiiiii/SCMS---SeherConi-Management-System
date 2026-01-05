package com.scms.service;

import com.scms.dao.RecipeDao;
import com.scms.model.Recipe;
import com.scms.model.RecipeItem;

import java.sql.SQLException;
import java.util.List;

public class RecipeService {

    private final RecipeDao recipeDao = new RecipeDao();

    public Recipe createRecipe(Recipe r, List<RecipeItem> items) {
        if (r == null) throw new ServiceException("recipe.required");
        if (r.getName() == null || r.getName().isBlank()) throw new ServiceException("recipe.name.required");
        if (items == null || items.isEmpty()) throw new ServiceException("recipe.items.required");
        try {
            return recipeDao.createWithItems(r, items);
        } catch (SQLException ex) {
            throw new ServiceException("Failed creating recipe", ex);
        }
    }

    public Recipe getRecipe(int id) {
        try {
            return recipeDao.findById(id).orElseThrow(() -> new ServiceException("recipe.not_found"));
        } catch (SQLException ex) {
            throw new ServiceException("Failed fetching recipe", ex);
        }
    }

    public List<Recipe> listRecipes() {
        try {
            return recipeDao.findAll();
        } catch (SQLException ex) {
            throw new ServiceException("Failed fetching recipes", ex);
        }
    }

    public void deleteRecipe(int id) {
        try {
            if (!recipeDao.delete(id)) throw new ServiceException("recipe.not_found");
        } catch (SQLException ex) {
            throw new ServiceException("Failed deleting recipe", ex);
        }
    }
}

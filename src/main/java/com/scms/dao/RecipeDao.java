package com.scms.dao;

import com.scms.config.DatabaseConfig;
import com.scms.model.Recipe;
import com.scms.model.RecipeItem;
import com.scms.util.ResultSetMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecipeDao {

    private final RecipeItemDao itemDao = new RecipeItemDao();

    public Recipe create(Recipe recipe) throws SQLException {
        String sql = "INSERT INTO recipes (name, description) VALUES (?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, recipe.getName());
            ps.setString(2, recipe.getDescription());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) recipe.setId(keys.getInt(1)); }
        }
        return recipe;
    }

    public Recipe createWithItems(Recipe recipe, List<RecipeItem> items) throws SQLException {
        String insertRecipeSql = "INSERT INTO recipes (name, description) VALUES (?, ?)";
        String insertItemSql = "INSERT INTO recipe_items (recipe_id, material_id, quantity, unit) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psRecipe = conn.prepareStatement(insertRecipeSql, Statement.RETURN_GENERATED_KEYS)) {
                psRecipe.setString(1, recipe.getName());
                psRecipe.setString(2, recipe.getDescription());
                psRecipe.executeUpdate();
                try (ResultSet keys = psRecipe.getGeneratedKeys()) {
                    if (keys.next()) recipe.setId(keys.getInt(1));
                }

                try (PreparedStatement psItem = conn.prepareStatement(insertItemSql, Statement.RETURN_GENERATED_KEYS)) {
                    for (RecipeItem it : items) {
                        psItem.setInt(1, recipe.getId());
                        psItem.setInt(2, it.getMaterialId());
                        psItem.setDouble(3, it.getQuantity());
                        psItem.setString(4, it.getUnit());
                        psItem.executeUpdate();
                        try (ResultSet keys = psItem.getGeneratedKeys()) { if (keys.next()) it.setId(keys.getInt(1)); }
                        it.setRecipeId(recipe.getId());
                        recipe.getItems().add(it);
                    }
                }

                conn.commit();
                return recipe;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public Optional<Recipe> findById(int id) throws SQLException {
        String sql = "SELECT * FROM recipes WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Recipe r = ResultSetMapper.mapRecipe(rs);
                    r.setItems(itemDao.findByRecipeId(r.getId()));
                    return Optional.of(r);
                }
            }
        }
        return Optional.empty();
    }

    public List<Recipe> findAll() throws SQLException {
        List<Recipe> list = new ArrayList<>();
        String sql = "SELECT * FROM recipes";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Recipe r = ResultSetMapper.mapRecipe(rs);
                r.setItems(itemDao.findByRecipeId(r.getId()));
                list.add(r);
            }
        }
        return list;
    }

    public Optional<Recipe> update(Recipe recipe) throws SQLException {
        String sql = "UPDATE recipes SET name = ?, description = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, recipe.getName());
            ps.setString(2, recipe.getDescription());
            ps.setInt(3, recipe.getId());
            int updated = ps.executeUpdate();
            if (updated > 0) return findById(recipe.getId());
        }
        return Optional.empty();
    }

    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM recipes WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}


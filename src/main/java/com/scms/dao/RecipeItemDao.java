package com.scms.dao;

import com.scms.config.DatabaseConfig;
import com.scms.model.RecipeItem;
import com.scms.util.ResultSetMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecipeItemDao {

    public RecipeItem create(RecipeItem item) throws SQLException {
        String sql = "INSERT INTO recipe_items (recipe_id, material_id, quantity, unit) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getRecipeId());
            ps.setInt(2, item.getMaterialId());
            ps.setDouble(3, item.getQuantity());
            ps.setString(4, item.getUnit());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) item.setId(keys.getInt(1)); }
        }
        return item;
    }

    public Optional<RecipeItem> findById(int id) throws SQLException {
        String sql = "SELECT * FROM recipe_items WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return Optional.of(ResultSetMapper.mapRecipeItem(rs)); }
        }
        return Optional.empty();
    }

    public List<RecipeItem> findByRecipeId(int recipeId) throws SQLException {
        List<RecipeItem> list = new ArrayList<>();
        String sql = "SELECT * FROM recipe_items WHERE recipe_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, recipeId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(ResultSetMapper.mapRecipeItem(rs)); }
        }
        return list;
    }

    public List<RecipeItem> findAll() throws SQLException {
        List<RecipeItem> list = new ArrayList<>();
        String sql = "SELECT * FROM recipe_items";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(ResultSetMapper.mapRecipeItem(rs));
        }
        return list;
    }

    public Optional<RecipeItem> update(RecipeItem item) throws SQLException {
        String sql = "UPDATE recipe_items SET recipe_id = ?, material_id = ?, quantity = ?, unit = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, item.getRecipeId());
            ps.setInt(2, item.getMaterialId());
            ps.setDouble(3, item.getQuantity());
            ps.setString(4, item.getUnit());
            ps.setInt(5, item.getId());
            int updated = ps.executeUpdate();
            if (updated > 0) return findById(item.getId());
        }
        return Optional.empty();
    }

    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM recipe_items WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}


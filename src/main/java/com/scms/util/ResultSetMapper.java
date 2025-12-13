package com.scms.util;

import com.scms.model.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class ResultSetMapper {

    public static User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        try { u.setPasswordHash(rs.getString("password_hash")); } catch (SQLException ignored) {}
        u.setRole(rs.getString("role"));
        return u;
    }

    public static Role mapRole(ResultSet rs) throws SQLException {
        Role r = new Role();
        r.setId(rs.getInt("id"));
        r.setName(rs.getString("name"));
        r.setDescription(rs.getString("description"));
        return r;
    }

    public static Material mapMaterial(ResultSet rs) throws SQLException {
        Material m = new Material();
        m.setId(rs.getInt("id"));
        m.setName(rs.getString("name"));
        m.setQuantity(rs.getDouble("quantity"));
        m.setUnit(rs.getString("unit"));
        m.setSupplier(rs.getString("supplier"));
        Timestamp t = rs.getTimestamp("updated_at");
        if (t != null) m.setUpdatedAt(t.toLocalDateTime());
        return m;
    }

    public static Assignment mapAssignment(ResultSet rs) throws SQLException {
        Assignment a = new Assignment();
        a.setId(rs.getInt("id"));
        a.setUserId(rs.getInt("user_id"));
        a.setMaterialId(rs.getInt("material_id"));
        a.setQuantity(rs.getDouble("quantity"));
        Timestamp t = rs.getTimestamp("assigned_at");
        if (t != null) a.setAssignedAt(t.toLocalDateTime());
        try { a.setStatus(rs.getString("status")); } catch (SQLException ignored) {}
        try { a.setNotes(rs.getString("notes")); } catch (SQLException ignored) {}
        return a;
    }

    public static Recipe mapRecipe(ResultSet rs) throws SQLException {
        Recipe r = new Recipe();
        r.setId(rs.getInt("id"));
        r.setName(rs.getString("name"));
        r.setDescription(rs.getString("description"));
        Timestamp t = rs.getTimestamp("created_at");
        if (t != null) r.setCreatedAt(t.toLocalDateTime());
        return r;
    }

    public static RecipeItem mapRecipeItem(ResultSet rs) throws SQLException {
        RecipeItem ri = new RecipeItem();
        ri.setId(rs.getInt("id"));
        ri.setRecipeId(rs.getInt("recipe_id"));
        ri.setMaterialId(rs.getInt("material_id"));
        ri.setQuantity(rs.getDouble("quantity"));
        ri.setUnit(rs.getString("unit"));
        return ri;
    }
}

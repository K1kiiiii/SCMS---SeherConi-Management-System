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
        // map minimum_quantity if present in resultset
        try { double min = rs.getDouble("minimum_quantity"); if (!rs.wasNull()) m.setMinimumQuantity(min); } catch (SQLException ignored) {}
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

    // map Task rows to Task model
    public static Task mapTask(ResultSet rs) throws SQLException {
        Task t = new Task();
        t.setId(rs.getInt("id"));
        t.setRecipeId(rs.getInt("recipe_id"));
        try { int at = rs.getInt("assigned_to"); if (!rs.wasNull()) t.setAssignedTo(at); } catch (SQLException ignored) {}
        try { int cb = rs.getInt("created_by"); if (!rs.wasNull()) t.setCreatedBy(cb); } catch (SQLException ignored) {}
        try { t.setQuantityTarget(rs.getDouble("quantity_target")); } catch (SQLException ignored) {}
        try { t.setUnit(rs.getString("unit")); } catch (SQLException ignored) {}
        try { java.sql.Date d = rs.getDate("deadline"); if (d != null) t.setDeadline(d.toLocalDate()); } catch (SQLException ignored) {}
        try { t.setStatus(rs.getString("status")); } catch (SQLException ignored) {}
        try { Timestamp c = rs.getTimestamp("created_at"); if (c != null) t.setCreatedAt(c.toLocalDateTime()); } catch (SQLException ignored) {}
        try { Timestamp s = rs.getTimestamp("started_at"); if (s != null) t.setStartedAt(s.toLocalDateTime()); } catch (SQLException ignored) {}
        try { Timestamp co = rs.getTimestamp("completed_at"); if (co != null) t.setCompletedAt(co.toLocalDateTime()); } catch (SQLException ignored) {}
        try { double pq = rs.getDouble("produced_quantity"); if (!rs.wasNull()) t.setProducedQuantity(pq); } catch (SQLException ignored) {}
        return t;
    }
}

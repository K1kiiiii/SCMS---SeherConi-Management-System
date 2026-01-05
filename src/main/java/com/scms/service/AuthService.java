package com.scms.service;

import com.scms.config.DatabaseConfig;
import com.scms.model.User;
import com.scms.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AuthService {

    public static User authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new ServiceException("auth.invalid_credentials");
        }

        String sql = "SELECT id, username, password_hash, role FROM users WHERE username = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int id = rs.getInt("id");
                    String dbUser = rs.getString("username");
                    String hash = rs.getString("password_hash");
                    String role = rs.getString("role");

                    // First, attempt bcrypt check (safe path)
                    try {
                        if (hash != null && PasswordUtil.checkPassword(password, hash)) {
                            User u = new User();
                            u.setId(id);
                            u.setUsername(dbUser);
                            u.setRole(role);
                            return u;
                        }
                    } catch (IllegalArgumentException iae) {
                        // thrown by BCrypt if hash format is invalid — fall through to legacy check
                    }

                    // Legacy fallback: if stored value isn't a bcrypt hash and matches the provided password
                    if (hash != null && !hash.startsWith("$2") && hash.equals(password)) {
                        // Migrate to bcrypt: hash the provided password and update DB
                        String newHash = PasswordUtil.hashPassword(password);
                        try (PreparedStatement ups = conn.prepareStatement("UPDATE users SET password_hash = ? WHERE id = ?")) {
                            ups.setString(1, newHash);
                            ups.setInt(2, id);
                            ups.executeUpdate();
                        } catch (SQLException ex) {
                            // If migration fails, log and continue to authenticate (we'll still accept login because plain matched)
                            System.err.println("Failed to migrate plain password to bcrypt for user id=" + id + ": " + ex.getMessage());
                        }

                        User u = new User();
                        u.setId(id);
                        u.setUsername(dbUser);
                        u.setRole(role);
                        return u;
                    }

                    // No match
                    throw new ServiceException("auth.invalid_credentials");
                }
                throw new ServiceException("auth.invalid_credentials");
            }
        } catch (SQLException ex) {
            throw new ServiceException("auth.error", ex);
        }
    }
}

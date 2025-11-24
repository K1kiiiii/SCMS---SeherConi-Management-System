package com.scms.service;

import com.scms.config.DatabaseConfig;
import com.scms.model.User;
import com.scms.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class AuthService {

    public static User authenticate(String username, String password) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT id, username, password_hash, role FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    if (PasswordUtil.checkPassword(password, hash)) {
                        return new User(rs.getInt("id"), rs.getString("username"), rs.getString("role"));
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
}

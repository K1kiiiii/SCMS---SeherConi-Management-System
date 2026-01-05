package com.scms.model;

public class User {
    private int id;
    private String username;
    private String role;
    private String passwordHash; // optional, stored in DB

    public User() {}
    public User(int id, String username, String role) {
        this.id = id; this.username = username; this.role = role;
    }

    // added constructor with passwordHash
    public User(int id, String username, String role, String passwordHash) {
        this.id = id; this.username = username; this.role = role; this.passwordHash = passwordHash;
    }

    // getters / setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}

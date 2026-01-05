package com.scms.service;

import com.scms.dao.UserDao;
import com.scms.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class UserService {

    private final UserDao userDao = new UserDao();

    public User createUser(String username, String plainPassword, String role) {
        if (username == null || username.isBlank()) throw new ServiceException("username.required");
        if (plainPassword == null || plainPassword.length() < 4) throw new ServiceException("password.too_short");
        if (role == null || role.isBlank()) throw new ServiceException("role.required");

        try {
            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(com.scms.util.PasswordUtil.hashPassword(plainPassword));
            user.setRole(role);
            return userDao.create(user);
        } catch (SQLException ex) {
            throw new ServiceException("Failed creating user", ex);
        }
    }

    public User getUserById(int id) {
        try {
            Optional<User> u = userDao.findById(id);
            return u.orElseThrow(() -> new ServiceException("user.not_found"));
        } catch (SQLException ex) {
            throw new ServiceException("Failed fetching user", ex);
        }
    }

    public List<User> getAllUsers() {
        try {
            return userDao.findAll();
        } catch (SQLException ex) {
            throw new ServiceException("Failed fetching users", ex);
        }
    }

    public User updateUser(User user) {
        if (user == null || user.getId() <= 0) throw new ServiceException("user.invalid");
        try {
            return userDao.update(user).orElseThrow(() -> new ServiceException("user.not_found"));
        } catch (SQLException ex) {
            throw new ServiceException("Failed updating user", ex);
        }
    }

    public void deleteUser(int id) {
        try {
            boolean ok = userDao.delete(id);
            if (!ok) throw new ServiceException("user.not_found");
        } catch (SQLException ex) {
            throw new ServiceException("Failed deleting user", ex);
        }
    }
}


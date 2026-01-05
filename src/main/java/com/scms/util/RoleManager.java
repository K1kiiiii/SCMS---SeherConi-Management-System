package com.scms.util;
import com.scms.model.User;

public class RoleManager {
    private static User loggedInUser;
    private RoleManager() {}
    public static User getLoggedInUser() {
        return loggedInUser;
    }
    public static void setLoggedInUser(User user) {
        loggedInUser = user;
    }
    public static boolean isAdmin() {
        if (loggedInUser == null ||  loggedInUser.getRole() == null) return false;
        String role = loggedInUser.getRole().toLowerCase();
        return role.equals("admin");
    }
    public static boolean isRadnik() {
        if (loggedInUser == null ||  loggedInUser.getRole() == null) return false;
        String role = loggedInUser.getRole().toLowerCase();
        return role.equals("radnik");
    }
    public static boolean isMagacioner() {
        if (loggedInUser == null ||  loggedInUser.getRole() == null) return false;
        String role = loggedInUser.getRole().toLowerCase();
        return role.equals("magacioner");
    }
}

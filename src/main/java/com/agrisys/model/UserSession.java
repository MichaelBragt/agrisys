package com.agrisys.model;

import com.agrisys.datalayer.entity.AppUserRecord;

/**
 * Singleton class to hold the current user session information.
 */
public class UserSession {
    private static UserSession instance;
    private AppUserRecord currentUser;

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public void setCurrentUser(AppUserRecord user) {
        this.currentUser = user;
    }

    public AppUserRecord getCurrentUser() {
        return currentUser;
    }

    public boolean isLandmand() {
        return currentUser != null && "Landmand".equalsIgnoreCase(currentUser.userRole());
    }

    public boolean isRaadgiver() {
        if (currentUser == null) return false;

        // Vi tjekker mod både 'Raadgiver' og 'Rådgiver', så vi er 100% skudsikre
        String role = currentUser.userRole(); // eller hvad Michaels felt hedder i AppUserRecord
        return "Raadgiver".equalsIgnoreCase(role) || "Rådgiver".equalsIgnoreCase(role);
    }

    public void logout() {
        this.currentUser = null;
    }
}

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
        return currentUser != null && "Rådgiver".equalsIgnoreCase(currentUser.userRole());
    }

    public void logout() {
        this.currentUser = null;
    }
}

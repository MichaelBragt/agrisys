package com.agrisys.config;

/**
 * Centralized Application Configuration.
 * Provides structural constants for the UI and operational keys for environment variables.
 *
 * @author Michael Bragt
 **/

public final class AppConfig {
    // Private constructor to prevent instantiation
    private AppConfig() {}

    public static final String APP_TITLE = "Agrisys - Precision Agriculture System";
    
    // Window Size Constraints
    public static final double MIN_WIDTH = 1280.0;
    public static final double MIN_HEIGHT = 768.0;

    // FXML Paths
    public static final String LOGIN_VIEW = "login-view.fxml";
    public static final String MAIN_VIEW = "main-view.fxml";
    public static final String HOME_VIEW = "home-view.fxml";
    public static final String PIGS_VIEW = "pigs-view.fxml";
}
package com.agrisys.controller;

import com.agrisys.AgrisysApplication;
import com.agrisys.config.AppConfig;
import com.agrisys.datalayer.dao.AppUserDAO;
import com.agrisys.datalayer.entity.AppUserRecord;
import com.agrisys.model.UserSession;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller ansvarlig for applikationens login-grænseflade.
 * Håndterer den indledende brugerautentificering mod databaselaget og etablerer den globale brugersession.
 * * @author Nicolai Dahl
 * @see "PS-02: Adgangsstyring - Arkitektonisk rollestyring og differentieret accessibility"
 * @see "FR-08: Brugere (landmand/rådgiver) skal kunne logge ind med unikt login"
 * @see "NFR-02: Architecture - Systemet skal opbygges i en lagdelt arkitektur (UI, Logik, Data)"
 */

public class LoginController {
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    // JavaFX login input-komponenter fra FXML
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;

    // Reference til datalaget jf. den lagdelte arkitektur (NFR-02)
    private AppUserDAO appUserDAO = new AppUserDAO();

    @FXML
    public void initialize() {
        // Initialisering hvis nødvendigt ved indlæsning i RAM
    }

    /**
     * Håndterer login-aktionen, når brugeren trykker på login-knappen.
     * Validerer input og forespørger datalaget for kryptografisk kontrol (SHA-256).
     */
    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // 1. INPUT-VALIDERING: Sikrer at felterne ikke er tomme før database-kald (Usability)
        if (username == null || username.trim().isEmpty()) {
            errorLabel.setText("Vælg venligst et brugernavn.");
            return;
        }

        if (password == null || password.trim().isEmpty()) {
            errorLabel.setText("Indtast venligst en adgangskode.");
            return;
        }

        try {
            // 2. AUTENTIFICERING: Kalder den sikre database-validering (SHA-256 tjek live i DB jf. FR-08)
            Optional<AppUserDAO.UserResult> loginResult = appUserDAO.validateLogin(username, password);

            if (loginResult.isPresent()) {
                // 3. AUTORISATION: Hvis koden matcher, hentes den fulde bruger-record til sessionsstyring
                Optional<AppUserRecord> userOpt = appUserDAO.findByUsername(username);
                if (userOpt.isPresent()) {
                    performLogin(userOpt.get());
                    return;
                }
            }

            // Defensiv sikkerhed: Ensartet fejlbesked uanset om det er koden eller brugernavnet, der er forkert
            errorLabel.setText("Forkert brugernavn eller adgangskode.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during login", e);
            errorLabel.setText("Fejl ved forbindelse til database.");
            }
    }

    /**
     * Afbryder programmet kontrolleret, hvis brugeren vælger at lukke vinduet.
     */
    @FXML
    private void handleExit() {
        Platform.exit();
    }

    /**
     * Etablerer den globale session og skifter JavaFX Scenegrafen over til hoveddashboardet.
     */
    private void performLogin(AppUserRecord user) {
        // Gemmer brugerens data og rettigheder i jeres trådsikre Singleton (Understøtter PS-02)
        UserSession.getInstance().setCurrentUser(user);
        
        try {
            // Hent det aktuelle vindue (Stage) og indlæs hoved-dashboardet live (FR-18 navigation)
            Stage stage = (Stage) usernameField.getScene().getWindow();
            FXMLLoader fxmlLoader = new FXMLLoader(AgrisysApplication.class.getResource(AppConfig.MAIN_VIEW));
            Scene scene = new Scene(fxmlLoader.load(), AppConfig.MIN_WIDTH, AppConfig.MIN_HEIGHT);
            stage.setScene(scene);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Kunne ikke indlæse main view", e);
            errorLabel.setText("Kunne ikke starte applikationen.");
        }
    }
}

package com.agrisys.controller;

import com.agrisys.AgrisysApplication;
import com.agrisys.config.AppConfig;
import com.agrisys.datalayer.dao.AppUserDAO;
import com.agrisys.datalayer.entity.AppUserRecord;
import com.agrisys.model.UserSession;
import javafx.application.Platform;
import javafx.collections.FXCollections;
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

public class LoginController {
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;

    private AppUserDAO appUserDAO = new AppUserDAO();

    @FXML
    public void initialize() {
        // Initialization if needed
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username == null || username.trim().isEmpty()) {
            errorLabel.setText("Vælg venligst et brugernavn.");
            return;
        }

        if (password == null || password.trim().isEmpty()) {
            errorLabel.setText("Indtast venligst en adgangskode.");
            return;
        }

        try {
            // Kalder vores nye, sikre validering, der klarer SHA-256 tjekket live i DB
            Optional<AppUserDAO.UserResult> loginResult = appUserDAO.validateLogin(username, password);

            if (loginResult.isPresent()) {
                // Siden login er godkendt, henter vi den fulde AppUserRecord til jeres UserSession
                Optional<AppUserRecord> userOpt = appUserDAO.findByUsername(username);
                if (userOpt.isPresent()) {
                    performLogin(userOpt.get());
                    return;
                }
            }

            // Alt fallback er fjernet! Hvis det ikke matcher databasens hashes, afvises man med det samme.
            errorLabel.setText("Forkert brugernavn eller adgangskode.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during login", e);
            errorLabel.setText("Fejl ved forbindelse til database.");
            }
    }
    
    @FXML
    private void handleExit() {
        Platform.exit();
    }

    private void performLogin(AppUserRecord user) {
        UserSession.getInstance().setCurrentUser(user);
        
        try {
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

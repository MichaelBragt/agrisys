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
 * * @author Nicolai Dahl (med optimeringer af gruppen)
 * @see "PS-02: Adgangsstyring - Arkitektonisk rollestyring og differentieret accessibility"
 * @see "FR-08: Brugere (landmand/rådgiver) skal kunne logge ind med unikt login"
 * @see "NFR-02: Architecture - Systemet skal opbygges i en lagdelt arkitektur (UI, Logik, Data)"
 */
public class LoginController {
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    // JavaFX login input-komponenter fra FXML
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    // OPTIMERING: Gjort final jf. Clean Code-principper (Tæt kobling minimeres)
    private final AppUserDAO appUserDAO = new AppUserDAO();

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
            // Her begynder flowfejlen... Inde i validateLogin metoden kalder vi findByUsername
            // så her længere nede, er det lidt redundant at vi kalder den igen
            // så vi kalder findByUsername 2 gange i hele login flowet, hvilket koster resourcer
            // validateLogin skulle have modtaget en fuld AppUserRecord istedet
            // Så havde det ikke være nødvendigt med det andet kald
            Optional<AppUserDAO.UserResult> loginResult = appUserDAO.validateLogin(username, password);

            if (loginResult.isPresent()) {
                // 3. AUTORISATION: Hvis koden matcher, hentes den fulde bruger-record til sessionsstyring
                // DETTE KALD, kunne være undgået
                Optional<AppUserRecord> userOpt = appUserDAO.findByUsername(username);
                if (userOpt.isPresent()) {
                    performLogin(userOpt.get());
                    return;
                }
            }

            // Defensiv sikkerhed: Ensartet fejlbesked uanset om det er koden eller brugernavnet, der er forkert
            errorLabel.setText("Forkert brugernavn eller adgangskode.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Databasefejl under login-eksekvering", e);
            errorLabel.setText("Fejl ved forbindelse til databasen.");
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
            // OPTIMERING: Hent det aktuelle vindue via scenegraf-roden og indlæs view stringent via getClass()
            Stage stage = (Stage) usernameField.getScene().getWindow();
            FXMLLoader fxmlLoader = new FXMLLoader(AgrisysApplication.class.getResource(AppConfig.MAIN_VIEW));

            // Indlæs scenen baseret på jeres NFR-arkitekturkrav til skærmstørrelse
            Scene scene = new Scene(fxmlLoader.load(), AppConfig.MIN_WIDTH, AppConfig.MIN_HEIGHT);
            stage.setScene(scene);
            stage.centerOnScreen(); // Sørger for at applikationen står flot centreret efter login
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Kritisk fejl under indlæsning af hoveddashboardet (AppConfig.MAIN_VIEW)", e);
            errorLabel.setText("Kunne ikke starte applikationen.");
        }
    }
}
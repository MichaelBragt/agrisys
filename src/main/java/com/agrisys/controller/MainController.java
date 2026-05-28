package com.agrisys.controller;

import com.agrisys.AgrisysApplication;
import com.agrisys.config.AppConfig;
import com.agrisys.model.UserSession;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrator og controller for applikationens hovedskal (Application Shell).
 * Håndterer overordnet top-level navigation, globale dialogbokse og sikker nedlukning af brugersessioner.
 * * @author Eirik Pran og Maria Alazzawi og Michael Bragt (med optimeringer af gruppen)
 * @see "PS-02: Adgangsstyring - Session Management og rettigheds-destruktion"
 * @see "FR-18: Navigation - Implementering af intuitiv home-, dialog- og tilbage-navigation"
 * @see "NFR-02: Architecture - Fungerer som præsentationslagets overordnede ramme (UI Shell)"
 */
public class MainController {
    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());

    /**
     * initialize kaldes automatisk af JavaFX, når hovedskallen (f.eks. med TabPane) indlæses i RAM.
     */
    @FXML
    public void initialize() {
        // Initialization logic for the TabPane shell
    }

    /**
     * Åbner en global dialogboks, der præsenterer den loggede brugers profiloplysninger og rettigheder.
     * Trækker informationer live fra jeres sessions-singleton jf. PS-02.
     */
    @FXML
    private void handleProfile() {
        try {
            // OPTIMERING: Gort final for stringent hukommelseshåndtering
            final Dialog<ButtonType> profileDialog = loadDialog("profile-dialog.fxml");
            final DialogPane dialogPane = profileDialog.getDialogPane();

            final Label lblUsername = (Label) dialogPane.lookup("#lblProfileUsername");
            final Label lblRole = (Label) dialogPane.lookup("#lblProfileRole");

            var currentUser = UserSession.getInstance().getCurrentUser();
            if (currentUser != null) {
                if (lblUsername != null) {
                    lblUsername.setText(currentUser.username());
                }
                if (lblRole != null) {
                    lblRole.setText(currentUser.userRole());
                }
            } else if (lblUsername != null) {
                lblUsername.setText("Ingen aktiv session");
            }

            profileDialog.showAndWait();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Kunne ikke indlæse profildialog.", e);
        }
    }

    /**
     * Håndterer log-ud flowet. Sikrer komplet destruktion af sessionsdata (Session Invalidation),
     * før scenegrafen kastes tilbage til login-skærmen (Udfører FR-08 og FR-18).
     */
    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            final Dialog<ButtonType> confirmLogout = loadDialog("logout-dialog.fxml");
            final Optional<ButtonType> result = confirmLogout.showAndWait();

            if (result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                // Destruer sessions-data (Session Invalidation jf. PS-02)
                UserSession.getInstance().logout();

                // Naviger sikkert tilbage til login-skærmen baseret på jeres AppConfig metadata
                final Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                final FXMLLoader fxmlLoader = new FXMLLoader(AgrisysApplication.class.getResource(AppConfig.LOGIN_VIEW));
                final Scene scene = new Scene(fxmlLoader.load(), AppConfig.MIN_WIDTH, AppConfig.MIN_HEIGHT);
                stage.setScene(scene);
                stage.centerOnScreen(); // Sikrer at loginvinduet står pænt centreret på skærmen efter logud
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Kunne ikke indlæse log ud-dialog.", e);
        }
    }

    /**
     * Privat hjælpemetode til generisk indlæsning af dialog-ressourcer jf. "Don't Repeat Yourself" (DRY).
     */
    private Dialog<ButtonType> loadDialog(String fileName) throws IOException {
        final URL dialogResource = Objects.requireNonNull(getClass().getResource("/com/agrisys/" + fileName));
        return FXMLLoader.load(dialogResource);
    }
}
package com.agrisys.controller;

import com.agrisys.DbConnect;
import com.agrisys.AgrisysApplication;
import com.agrisys.config.AppConfig;
import com.agrisys.model.UserSession;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrator for the main application shell.
 * Handles top-level navigation and global application state.
 */
public class MainController {
    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());

    public void initialize() {
        // Initialization logic for the TabPane shell

        // Just a line used for testing db connection
        // is to be removed
        System.out.println("Before D");
        DbConnect connection = DbConnect.UNIQUE_CONNECT;
        System.out.println("Before D");
    }

    @FXML
    private void handleProfile() {
        try {
            // 1. Indlæs dialogen via jeres eksisterende loadDialog metode
            Dialog<ButtonType> profileDialog = loadDialog("profile-dialog.fxml");

            // 2. Hent dialogens underliggende DialogPane
            DialogPane dialogPane = profileDialog.getDialogPane();

            // 3. Slå jeres to labels op via deres CSS/FXML-id (#navn)
            Label lblUsername = (Label) dialogPane.lookup("#lblProfileUsername");
            Label lblRole = (Label) dialogPane.lookup("#lblProfileRole");

            // 4. Hent den aktive bruger fra jeres skudsikre UserSession
            var session = com.agrisys.model.UserSession.getInstance();
            var currentUser = session.getCurrentUser();

            if (currentUser != null) {
                // Sæt brugernavnet live (fx "Landmand" eller "Rådgiver")
                if (lblUsername != null) {
                    lblUsername.setText(currentUser.username());
                }

                // Sæt rollen live
                if (lblRole != null) {
                    lblRole.setText(currentUser.userRole());
                }
            } else {
                if (lblUsername != null) lblUsername.setText("Ingen aktiv session");
            }

            // 5. Vis dialogen til brugeren
            profileDialog.showAndWait();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Kunne ikke indlæse profildialog.", e);
        }
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            Dialog<ButtonType> confirmLogout = loadDialog("logout-dialog.fxml");
            Optional<ButtonType> result = confirmLogout.showAndWait();
            if (result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                UserSession.getInstance().logout();
                Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                FXMLLoader fxmlLoader = new FXMLLoader(AgrisysApplication.class.getResource(AppConfig.LOGIN_VIEW));
                Scene scene = new Scene(fxmlLoader.load(), AppConfig.MIN_WIDTH, AppConfig.MIN_HEIGHT);
                stage.setScene(scene);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Kunne ikke indlæse log ud-dialog.", e);
        }
    }

    private Dialog<ButtonType> loadDialog(String fileName) throws IOException {
        URL dialogResource = Objects.requireNonNull(getClass().getResource("/com/agrisys/" + fileName));
        return FXMLLoader.load(dialogResource);
    }
}

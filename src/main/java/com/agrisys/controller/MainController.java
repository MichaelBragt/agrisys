package com.agrisys.controller;

import com.agrisys.AgrisysApplication;
import com.agrisys.DbConnect;
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
 * Orchestrator for the main application shell.
 * Handles top-level navigation and global application state.
 *
 */
public class MainController {
    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());

    public void initialize() {
        // Initialization logic for the TabPane shell
    }

    @FXML
    private void handleProfile() {
        try {
            Dialog<ButtonType> profileDialog = loadDialog("profile-dialog.fxml");
            DialogPane dialogPane = profileDialog.getDialogPane();

            Label lblUsername = (Label) dialogPane.lookup("#lblProfileUsername");
            Label lblRole = (Label) dialogPane.lookup("#lblProfileRole");

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

package com.agrisys.controller;

import com.agrisys.DbConnect;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import java.util.Optional;

/**
 * Orchestrator for the main application shell.
 * Handles top-level navigation and global application state.
 */
public class MainController {

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
        Alert profileDialog = new Alert(Alert.AlertType.INFORMATION);
        profileDialog.setTitle("Profil");
        profileDialog.setHeaderText("Brugerprofil");

        GridPane profileInfo = new GridPane();
        profileInfo.setHgap(12);
        profileInfo.setVgap(8);
        profileInfo.addRow(0, new Label("Bruger:"), new Label("Ikke angivet"));
        profileInfo.addRow(1, new Label("Gård:"), new Label("Ikke angivet"));
        profileInfo.addRow(2, new Label("Rolle:"), new Label("Ikke angivet"));

        profileDialog.getDialogPane().setContent(profileInfo);
        profileDialog.getButtonTypes().setAll(new ButtonType("Luk", ButtonBar.ButtonData.OK_DONE));
        profileDialog.showAndWait();
    }

    @FXML
    private void handleLogout() {
        Alert confirmLogout = new Alert(Alert.AlertType.CONFIRMATION);
        confirmLogout.setTitle("Log ud");
        confirmLogout.setHeaderText("Vil du logge ud?");
        confirmLogout.setContentText("Programmet lukkes, hvis du fortsætter.");

        ButtonType logoutButton = new ButtonType("Log ud", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Annuller", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmLogout.getButtonTypes().setAll(logoutButton, cancelButton);

        Optional<ButtonType> result = confirmLogout.showAndWait();
        if (result.isPresent() && result.get() == logoutButton) {
            Platform.exit();
        }
    }
}

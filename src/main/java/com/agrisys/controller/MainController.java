package com.agrisys.controller;

import com.agrisys.DbConnect;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;

import java.io.IOException;
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
        try {
            Dialog<ButtonType> profileDialog = loadDialog("profile-dialog.fxml");
            profileDialog.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout() {
        try {
            Dialog<ButtonType> confirmLogout = loadDialog("logout-dialog.fxml");
            Optional<ButtonType> result = confirmLogout.showAndWait();
            if (result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                Platform.exit();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private Dialog<ButtonType> loadDialog(String fileName) throws IOException {
        return FXMLLoader.load(getClass().getResource("/com/agrisys/" + fileName));
    }
}

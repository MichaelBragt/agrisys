package com.agrisys.controller;

import com.agrisys.DbConnect;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Orchestrator for the main application shell.
 * Handles top-level navigation and global application state.
 */
public class MainController {

    @FXML
    private TabPane mainTabPane;

    public void initialize() {
        // Initialization logic for the TabPane shell
        addLogoutButton();

        // Just a line used for testing db connection
        // is to be removed
        System.out.println("Before D");
        DbConnect connection = DbConnect.UNIQUE_CONNECT;
        System.out.println("Before D");
    }

    private void addLogoutButton() {
        Button logoutButton = new Button("Log ud");
        logoutButton.getStyleClass().add("logout-button");
        logoutButton.setOnAction(event -> handleLogout());

        HBox topBar = new HBox(logoutButton);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        VBox mainLayout = (VBox) mainTabPane.getParent();
        mainLayout.getChildren().add(0, topBar);
    }

    private void handleLogout() {
        Alert confirmLogout = new Alert(Alert.AlertType.CONFIRMATION);
        confirmLogout.setTitle("Log ud");
        confirmLogout.setHeaderText("Vil du logge ud?");
        confirmLogout.setContentText("Programmet lukkes, hvis du fortsætter.");

        Optional<ButtonType> result = confirmLogout.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Platform.exit();
        }
    }
}

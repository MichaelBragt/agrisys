package com.agrisys.controller;

import com.agrisys.DbConnect;
import javafx.fxml.FXML;
import javafx.scene.control.TabPane;

/**
 * Orchestrator for the main application shell.
 * Handles top-level navigation and global application state.
 */
public class MainController {

    @FXML
    private TabPane mainTabPane;

    public void initialize() {
        // Initialization logic for the TabPane shell

        // Just a line used for testing db connection
        // is to be removed
        DbConnect connection = DbConnect.UNIQUE_CONNECT;

    }
}
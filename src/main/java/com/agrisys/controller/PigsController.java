package com.agrisys.controller;

import com.agrisys.Utils.Gauge;
import com.agrisys.Utils.UIErrorReport;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Controller for the Pig Management view.
 * Handles biological data tracking, RFID tag assignments, and health records.
 */
public class PigsController {

    public StackPane gaugeContainer2;

    /**
     * Initializes the controller. 
     * This is where we will eventually bind the TableView for pig records
     * and set up the search logic for RFID tags.
     */
    @FXML
    public void initialize() {
        // Implementation for PS-02 (Business Logic Layer) integration goes here
        Gauge gauge = new Gauge(50);
        gauge.updateStatus(47);
        gaugeContainer2.getChildren().add(gauge);
    }

    /**
     * Opens the modal dialog for registering a new pig.
     * This method fulfills the requirement of modularity by decoupling the main view
     * logic from the specific registration workflow.
     */
    @FXML
    private void handleOpenRegistration() {
        try {
            // Architectural Note: We use a specific loader to instantiate the dialog view.
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/agrisys/pig-registration-dialog.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Registrer Ny Gris");
            
            // Modality ensures the user focuses on the task at hand (Data Entry).
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(gaugeContainer2.getScene().getWindow());
            
            stage.setScene(new Scene(root));
            stage.showAndWait();
        } catch (IOException e) {
            UIErrorReport.showDatabaseError(e);
        }
    }
}
package com.agrisys.controller;

import com.agrisys.model.PigSummary;
import com.agrisys.service.PigService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.IOException;
import java.time.LocalDate;

/**
 * Controller for the Pigs Management view.
 * Follows MVC pattern by delegating logic to PigService.
 */
public class PigsController {

    @FXML private TableView<PigSummary> pigTable;
    @FXML private TableColumn<PigSummary, String> colAnimalNumber;
    @FXML private TableColumn<PigSummary, String> colResponder;
    @FXML private TableColumn<PigSummary, Integer> colLocation;
    @FXML private TableColumn<PigSummary, LocalDate> colBirthDate;
    @FXML private TableColumn<PigSummary, Double> colWeight;
    @FXML private TableColumn<PigSummary, Double> colFCR;

    private final PigService pigService;
    private final ObservableList<PigSummary> masterData = FXCollections.observableArrayList();

    public PigsController() {
        this.pigService = new PigService();
    }

    @FXML
    public void initialize() {
        setupTable();
        loadData();
    }

    private void setupTable() {
        // Using Type-Safe Lambda factories compatible with Java Records
        colAnimalNumber.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().animalNumber()));
        colResponder.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().responderId()));
        colLocation.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().locationId()));
        colBirthDate.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().birthDate()));
        colWeight.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().weight()));
        colFCR.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().fcr()));
        
        pigTable.setItems(masterData);
    }

    private void loadData() {
        // Offload to background thread if the dataset grows significantly
        masterData.setAll(pigService.getActivePigDashboardData());
    }

    @FXML
    private void handleOpenRegistration() {
        try {
            // Load the FXML for the registration dialog
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/agrisys/pig-registration-dialog.fxml"));
            Parent root = loader.load();

            // Create and configure a new Stage (Window)
            Stage stage = new Stage();
            stage.setTitle("Registrer Ny Gris");
            stage.initModality(Modality.APPLICATION_MODAL); // Block interaction with main window
            stage.initOwner(pigTable.getScene().getWindow()); // Set parent window for centering
            stage.setScene(new Scene(root));

            // Show and wait blocks execution here until the window is closed
            stage.showAndWait();

            // Refresh the table data to show the new pig if it was saved
            loadData();
        } catch (IOException e) {
            // Log the error - in production, this should trigger an Alert to the user
            e.printStackTrace();
        }
    }
}
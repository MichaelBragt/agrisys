package com.agrisys.controller;

import com.agrisys.datalayer.entity.LocationRecord;
import com.agrisys.service.LocationService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.List;

/**
 * Controller til administration og CRUD-håndtering af staldens bokse og stier.
 * Gør det muligt for landmanden at vedligeholde de fysiske rammer i svineproduktionen live.
 * * @author Nicolai Dahl
 * @see "PS-03: Interoperabilitet - Strukturering og persistent besætningsstyring"
 * @see "FR-19: CRUD Båse - Landmanden skal kunne oprette og administrere båse/bokse"
 * @see "NFR-02: Architecture - Systemet skal opbygges i en lagdelt arkitektur (UI, Logik, Data)"
 */

public class LocationsController {

    @FXML private TableView<LocationRecord> locationTable;
    @FXML private TableColumn<LocationRecord, Integer> colId;
    @FXML private TableColumn<LocationRecord, String> colName;
    @FXML private TextField nameField;
    @FXML private Label messageLabel;

    private final LocationService locationService = new LocationService();
    private final ObservableList<LocationRecord> locationList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().locationId()).asObject());
        colName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().locationName()));

        locationTable.setItems(locationList);

        locationTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                nameField.setText(newSelection.locationName());
                messageLabel.setText("");
            }
        });

        loadLocations();
    }

    private void loadLocations() {
        locationList.clear();
        List<LocationRecord> locations = locationService.getAllLocations();
        locationList.addAll(locations);
    }

    @FXML
    private void handleCreate() {
        String name = nameField.getText();
        try {
            locationService.createLocation(name);
            showSuccess("Lokation oprettet.");
            handleClear();
            loadLocations();
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (SQLException e) {
            showError("Fejl ved oprettelse af lokation: " + e.getMessage());
        }
    }

    @FXML
    private void handleUpdate() {
        LocationRecord selected = locationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Vælg venligst en lokation at opdatere.");
            return;
        }

        String newName = nameField.getText();
        try {
            boolean updated = locationService.updateLocation(selected.locationId(), newName);
            if (updated) {
                showSuccess("Lokation opdateret.");
                handleClear();
                loadLocations();
            } else {
                showError("Kunne ikke opdatere lokation.");
            }
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (SQLException e) {
            showError("Fejl ved opdatering: " + e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        LocationRecord selected = locationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Vælg venligst en lokation at slette.");
            return;
        }

        try {
            boolean deleted = locationService.deleteLocation(selected.locationId());
            if (deleted) {
                showSuccess("Lokation slettet.");
                handleClear();
                loadLocations();
            }
        } catch (IllegalStateException e) {
            showWarningDialog("Advarsel: Kan ikke slette lokation", e.getMessage());
            showError(e.getMessage());
        } catch (SQLException e) {
            showError("Fejl ved sletning: " + e.getMessage());
        }
    }

    @FXML
    private void handleClear() {
        nameField.clear();
        locationTable.getSelectionModel().clearSelection();
        messageLabel.setText("");
    }

    @FXML
    private void handleClose() {
        ((Stage) locationTable.getScene().getWindow()).close();
    }

    private void showSuccess(String message) {
        messageLabel.setTextFill(Color.GREEN);
        messageLabel.setText(message);
    }

    private void showError(String message) {
        messageLabel.setTextFill(Color.RED);
        messageLabel.setText(message);
    }

    private void showWarningDialog(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Advarsel");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
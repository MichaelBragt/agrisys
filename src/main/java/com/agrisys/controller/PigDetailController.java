package com.agrisys.controller;

import com.agrisys.Utils.*;
import com.agrisys.Utils.UIErrorReport;
import com.agrisys.dto.chart.ChartSeriesData;
import com.agrisys.datalayer.entity.LocationRecord;
import com.agrisys.model.view.PigDetailDTO;
import com.agrisys.model.view.PigSummary;
import com.agrisys.service.LocationService;
import com.agrisys.service.PigDetailsService;
import com.agrisys.model.UserSession;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the Pig Detail popup..
 */
public class PigDetailController {
    // Declaring varous UI elements
    @FXML private Label lblAnimalNumber, lblResponderId, lblLocation, lblWeight, lblChartHeader;
    @FXML private Button btnVegt, btnFoder, btnFcr;
    @FXML private DatePicker dpBirthDate;
    @FXML private ComboBox<String> cbStatus;
    @FXML private ComboBox<LocationRecord> cbLocation;
    @FXML private StackPane gaugeContainer, chartContainer;
    @FXML private Button btnEdit, btnSave, btnRemoveResponder;
    @FXML private Label lblWeightGained;
    @FXML private Label lblTotalFeed;

    // declaring services, DTO's and vars we need
    private final PigDetailsService service = new PigDetailsService();
    private final LocationService locationService = new LocationService();
    private PigDetailDTO currentPig;
    private boolean isEditMode = false;

    // Styring af den aktive graftype
    private enum ChartType { VEGT, FODER, FCR }
    private ChartType activeChartType = ChartType.VEGT; // Standardgraf ved åbning

    @FXML
    public void initialize() {
        cbStatus.getItems().addAll("Aktiv", "Slagtet", "Syg");
        setupLocationComboBox();

        if (UserSession.getInstance().isRaadgiver()) {
            btnEdit.setVisible(false);
            btnEdit.setManaged(false);
            btnRemoveResponder.setVisible(false);
            btnRemoveResponder.setManaged(false);
        }
    }

    private void setupLocationComboBox() {
        cbLocation.setItems(FXCollections.observableArrayList(locationService.getAllLocations()));
        cbLocation.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocationRecord loc) {
                return loc == null ? "" : loc.locationId() + " - " + loc.locationName();
            }

            @Override
            public LocationRecord fromString(String string) {
                return null;
            }
        });
    }

    public void initData(PigSummary summary) {
        refreshData(summary.animalNumber());
    }

    private void refreshData(String animalNumber) {
        try {
            Optional<PigDetailDTO> details = service.getDetailedInfo(animalNumber);
            details.ifPresent(dto -> {
                this.currentPig = dto;
                lblAnimalNumber.setText(dto.animalNumber());
                lblResponderId.setText(dto.responderId() != null ? dto.responderId() : "Ingen");
                lblLocation.setText(dto.locationName() != null ? dto.locationName() : "N/A");
                
                // Select current location in ComboBox
                cbLocation.getItems().stream()
                    .filter(loc -> loc.locationName().equals(dto.locationName()))
                    .findFirst()
                    .ifPresent(loc -> cbLocation.setValue(loc));

                dpBirthDate.setValue(dto.birthDate());
                cbStatus.setValue(dto.status());

                btnRemoveResponder.setDisable(dto.responderId() == null);

                // Handle potential 0 or null weights
                lblWeight.setText(dto.currentWeight() > 0 ? String.format("%.2f kg", dto.currentWeight()) : "Ingen data");

                // --- HER LÆGGER DU DE TO NYE REGLER IND ---

                // 1. Total Foderindtag (omregnes fra gram til kg)
                // (Hvis jeres DTO metode hedder noget andet, fx getTotalFeed(), retter I bare navnet)
                double totalFeedKg = dto.totalFeed() / 1000.0;
                lblTotalFeed.setText(String.format("%.2f kg foder", totalFeedKg));

                // 2. Total Tilvækst (Aktuel vægt minus startvægt)
                double startWeightKg = dto.startWeight() / 1000.0;
                double growthKg = dto.currentWeight() - startWeightKg;

                if (growthKg > 0 && dto.currentWeight() > 0) {
                    lblWeightGained.setText(String.format("%.2f kg tilvækst", growthKg));
                } else {
                    lblWeightGained.setText("0.00 kg (Mangler målinger)");
                }

                // ------------------------------------------

                setupVisuals(dto);
            });
        } catch (SQLException e) {
            UIErrorReport.showDatabaseError(e);
        }
    }

    private void setupVisuals(PigDetailDTO dto) {
        // 1. Setup Gauge
        gaugeContainer.getChildren().clear();
        Gauge fcrGauge = new Gauge(70);
        fcrGauge.setFcrValue(dto.fcr()); // Real calculation from DB
        gaugeContainer.getChildren().add(fcrGauge);

        // 2. Setup Chart using existing ChartBuilder
        if (dto.assignmentId() != null) {
            try {
                ChartSeriesData series = service.getPigWeightHistory(dto.animalNumber(), dto.assignmentId());
                chartContainer.getChildren().clear();
                chartContainer.getChildren().add(AgrisysChartBuilder.buildLineChart(
                    "", "Dato", "Vægt (kg)", "Vægt (kg)", List.of(series)
                ));
            } catch (SQLException e) {
                System.err.println("Could not load chart data: " + e.getMessage());
            }
        }
    }
    /**
     * NY METODE: Henter den korrekte data og tegner grafen baseret på knappernes tilstand
     */
    private void opdaterIndividuelGraf(PigDetailDTO dto) {
        if (dto == null || dto.assignmentId() == null) return;

        try {
            chartContainer.getChildren().clear();

            // Opdater knappernes visuelle styling i forhold til hvad der er aktivt
            btnVegt.setStyle(activeChartType == ChartType.VEGT ? "-fx-background-color: #2196F3; -fx-text-fill: white;" : "");
            btnFoder.setStyle(activeChartType == ChartType.FODER ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "");
            btnFcr.setStyle(activeChartType == ChartType.FCR ? "-fx-background-color: #FF9800; -fx-text-fill: white;" : "");

            ChartSeriesData series;
            javafx.scene.chart.LineChart<String, Number> chart;

            // Switch-case der håndterer datakilden og opdaterer FXML-overskriften dynamic
            switch (activeChartType) {
                case FODER -> {
                    lblChartHeader.setText("Foderindtag pr. besøg");
                    series = service.getPigFeedHistory(dto.animalNumber(), dto.assignmentId());
                    chart = AgrisysChartBuilder.buildLineChart("", "Dato", "Foder (gram)", "Foderindtag (g)", List.of(series));
                }
                case FCR -> {
                    lblChartHeader.setText("Individuel FCR udvikling");
                    series = service.getPigFcrHistory(dto.animalNumber(), dto.assignmentId());
                    chart = AgrisysChartBuilder.buildLineChart("", "Dato", "FCR Værdi", "FCR ratio", List.of(series));
                }
                default -> { // VEGT
                    lblChartHeader.setText("Vægtudvikling over tid");
                    series = service.getPigWeightHistory(dto.animalNumber(), dto.assignmentId());
                    chart = AgrisysChartBuilder.buildLineChart("", "Dato", "Vægt (kg)", "Vægt (kg)", List.of(series));
                }
            }

            chartContainer.getChildren().add(chart);

        } catch (SQLException e) {
            System.err.println("Could not load chart data: " + e.getMessage());
        }
    }

    // TILFØJET: De tre onAction-metoder som kaldes fra jeres FXML-knapper
    @FXML
    private void handleVegtChartAction() {
        activeChartType = ChartType.VEGT;
        opdaterIndividuelGraf(currentPig);
    }

    @FXML
    private void handleFoderChartAction() {
        activeChartType = ChartType.FODER;
        opdaterIndividuelGraf(currentPig);
    }

    @FXML
    private void handleFcrChartAction() {
        activeChartType = ChartType.FCR;
        opdaterIndividuelGraf(currentPig);
    }

    @FXML
    private void handleToggleEdit() {
        isEditMode = !isEditMode;

        // Toggle view elements
        lblLocation.setVisible(!isEditMode);
        lblLocation.setManaged(!isEditMode);
        cbLocation.setVisible(isEditMode);
        cbLocation.setManaged(isEditMode);

        dpBirthDate.setDisable(!isEditMode);
        cbStatus.setDisable(!isEditMode);
        btnSave.setVisible(isEditMode);
        btnEdit.setText(isEditMode ? "Annuller" : "Rediger");
    }

    @FXML
    private void handleSave() {
        String selectedStatus = cbStatus.getValue();
        LocalDate selectedBirthDate = dpBirthDate.getValue();
        LocationRecord selectedLocation = cbLocation.getValue();
        boolean shouldRemove = false;

        if (!selectedStatus.equals("Aktiv") && currentPig.responderId() != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, 
                "Ønsker du at fjerne responder " + currentPig.responderId() + " fra denne gris?", 
                ButtonType.YES, ButtonType.NO);
            alert.setTitle("Fjern Responder");
            Optional<ButtonType> result = alert.showAndWait();
            shouldRemove = (result.isPresent() && result.get() == ButtonType.YES);
        }

        try {
            Integer newLocId = selectedLocation != null ? selectedLocation.locationId() : null;
            service.updatePigDetails(currentPig.animalNumber(), selectedStatus, selectedBirthDate, shouldRemove, currentPig.responderId(), newLocId);
            handleToggleEdit();
            refreshData(currentPig.animalNumber());
        } catch (SQLException e) {
            UIErrorReport.showDatabaseError(e);
        }
    }

    @FXML
    private void handleRemoveResponder() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, 
            "Er du sikker på at du vil fjerne responderen nu?", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    service.updatePigDetails(currentPig.animalNumber(), currentPig.status(), currentPig.birthDate(), true, currentPig.responderId(), null);
                    refreshData(currentPig.animalNumber());
                } catch (SQLException e) {
                    UIErrorReport.showDatabaseError(e);
                }
            }
        });
    }

    @FXML
    private void handleClose() {
        lblAnimalNumber.getScene().getWindow().hide();
    }
}
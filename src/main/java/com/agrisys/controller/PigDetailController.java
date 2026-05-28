package com.agrisys.controller;

import com.agrisys.Utils.AgrisysChartBuilder;
import com.agrisys.Utils.Gauge;
import com.agrisys.Utils.UIErrorReport;
import com.agrisys.dto.chart.ChartPoint;
import com.agrisys.dto.chart.ChartSeriesData;
import com.agrisys.datalayer.entity.LocationRecord;
import com.agrisys.model.view.PigDetailDTO;
import com.agrisys.model.view.PigSummary;
import com.agrisys.service.LocationService;
import com.agrisys.service.PigDetailsService;
import com.agrisys.model.UserSession;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Controller til popup-modalen for en enkelt gris' detaljer.
 * Håndterer individuel vækstovervågning, performance-grafer samt deaktivering af IoT-hardware.
 * * @author Eirik Pran og Michael Bragt og Nicolai Dahl (med optimeringer af gruppen)
 * @see "PS-01: Datavisualisering - Intuitive dashboards frem for rå tabeller"
 * @see "PS-02: Adgangsstyring - Arkitektonisk rollestyring og individuel CRUD"
 * @see "FR-03: Landmanden skal kunne rette stamdata, gruppe, lokation og foderindstillinger"
 * @see "FR-04: Landmanden skal kunne stoppe registreringen af en gris (v. sygdom/skade)"
 * @see "FR-05: Systemet skal vise vægt- og spiseaktivitet for en specifik gris"
 * @see "FR-09: Systemet skal automatisk beregne FCR og Gennemsnitlig Daglig Tilvækst (ADG)"
 * @see "FR-15: Rådgiver-interfacet skal være begrænset til Read-only på grisens stamdata"
 */
public class PigDetailController {

    // JavaFX UI-elementer til præsentation af stamdata
    @FXML private Label lblAnimalNumber, lblResponderId, lblLocation, lblWeight, lblChartHeader;
    @FXML private Button btnVegt, btnFoder, btnFcr;

    // Input-komponenter til redigering (Underlagt FR-03 og FR-15)
    @FXML private DatePicker dpBirthDate;
    @FXML private ComboBox<String> cbStatus;
    @FXML private ComboBox<LocationRecord> cbLocation;

    // UI-containere til grafer og gauges (Understøtter FR-05 og FR-09)
    @FXML private StackPane gaugeContainer, chartContainer;
    @FXML private Button btnEdit, btnSave, btnRemoveResponder;
    @FXML private Label lblWeightGained;
    @FXML private Label lblTotalFeed;

    // Services (Gjort final jf. Clean Code-principper for trådsikkerhed)
    private final PigDetailsService service = new PigDetailsService();
    private final LocationService locationService = new LocationService();
    private PigDetailDTO currentPig;
    private boolean isEditMode = false;

    // Styring af den aktive graftype på profil-dashboardet (FR-05 / FR-10)
    private enum ChartType { VEGT, FODER, FCR }
    private ChartType activeChartType = ChartType.VEGT; // Standardgraf ved åbning

    @FXML
    public void initialize() {
        cbStatus.getItems().addAll("Aktiv", "Slagtet", "Syg");
        setupLocationComboBox();

        // =========================================================================
        // ROLE-BASED ACCESS CONTROL (RBAC) - AUTORISATION (PS-02 / FR-15)
        // =========================================================================
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

    /**
     * Henter de nyeste, konsoliderede data fra databasen live jf. NFR-02.
     */
    private void refreshData(String animalNumber) {
        try {
            Optional<PigDetailDTO> details = service.getDetailedInfo(animalNumber);
            details.ifPresent(dto -> {
                this.currentPig = dto;

                lblAnimalNumber.setText(dto.animalNumber());
                lblResponderId.setText(dto.responderId() != null ? dto.responderId() : "Ingen");
                lblLocation.setText(dto.locationName() != null ? dto.locationName() : "N/A");

                // Vælg aktuel sti i ComboBoxen
                cbLocation.getItems().stream()
                        .filter(loc -> loc.locationName().equals(dto.locationName()))
                        .findFirst()
                        .ifPresent(loc -> cbLocation.setValue(loc));

                dpBirthDate.setValue(dto.birthDate());
                cbStatus.setValue(dto.status());

                // Hvis grisen ikke har et øremærke på, deaktiveres "Fjern hardware"-knappen (Domain Rule 1.2)
                btnRemoveResponder.setDisable(dto.responderId() == null);

                lblWeight.setText(dto.currentWeight() > 0 ? String.format("%.2f kg", dto.currentWeight()) : "Ingen data");

                // 1. Total Foderindtag (omregnes fra gram til kg jf. PS-01)
                double totalFeedKg = dto.totalFeed() / 1000.0;
                lblTotalFeed.setText(String.format("%.2f kg foder", totalFeedKg));

                // 2. Total Tilvækst (Aktuel vægt minus startvægt jf. FR-09)
                double startWeightKg = dto.startWeight() / 1000.0;
                double growthKg = dto.currentWeight() - startWeightKg;

                if (growthKg > 0 && dto.currentWeight() > 0) {
                    lblWeightGained.setText(String.format("%.2f kg tilvækst", growthKg));
                } else {
                    lblWeightGained.setText("0.00 kg (Mangler målinger)");
                }

                setupVisuals(dto);
            });
        } catch (SQLException e) {
            UIErrorReport.showDatabaseError(e);
        }
    }

    /**
     * Forbereder og injicerer de grafiske elementer på grisens profil (FR-05 og FR-09).
     */
    private void setupVisuals(PigDetailDTO dto) {
        // 1. Setup Gauge Måler (FR-09 FCR visning)
        gaugeContainer.getChildren().clear();
        Gauge fcrGauge = new Gauge(70);
        fcrGauge.setFcrValue(dto.fcr());
        gaugeContainer.getChildren().add(fcrGauge);

        // 2. OPTIMERING: Kalder den fælles graf-pipeline for at sikre ensartet styling ved vinduets åbning
        opdaterIndividuelGraf(dto);
    }

    /**
     * Central graf-pipeline. Tegner den valgte historiske tidslinje asynkront jf. FR-05.
     */
    private void opdaterIndividuelGraf(PigDetailDTO dto) {
        if (dto == null || dto.assignmentId() == null) return;

        try {
            chartContainer.getChildren().clear();

            // Opdater knappernes visuelle aktive CSS-styling live i præsentationslaget (PS-01)
            btnVegt.setStyle(activeChartType == ChartType.VEGT ? "-fx-background-color: #2196F3; -fx-text-fill: white;" : "");
            btnFoder.setStyle(activeChartType == ChartType.FODER ? "-fx-background-color: #4CAF50; -fx-text-fill: white;" : "");
            btnFcr.setStyle(activeChartType == ChartType.FCR ? "-fx-background-color: #FF9800; -fx-text-fill: white;" : "");

            ChartSeriesData series;
            LineChart<String, Number> chart;

            // Switch-case der trækker den korrekte historik via vores Service (NFR-02)
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
            System.err.println("Kunne ikke indlæse historisk graf-data: " + e.getMessage());
            UIErrorReport.showDatabaseError(e); // OPTIMERING: Sikrer grafisk fejlbesked frem for rå konsol-log
        }
    }

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

        // FORRETNINGSREGEL (FR-04): Hvis en gris stoppes (f.eks. pga. sygdom/slagtes), spørges der om hardwaren skal frigives
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
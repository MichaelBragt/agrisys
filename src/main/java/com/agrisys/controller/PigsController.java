package com.agrisys.controller;

import com.agrisys.Utils.AgrisysChartBuilder;
import com.agrisys.Utils.Gauge; // Sørg for at importere jeres Gauge klasse
import com.agrisys.Utils.UIErrorReport;
import com.agrisys.dto.ChartSeriesData;
import com.agrisys.model.LocationRecord;
import com.agrisys.model.PigSummary;
import com.agrisys.service.ChartService;
import com.agrisys.service.LocationService;
import com.agrisys.service.PigService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PigsController {

    // We declare table column types and datatypes here
    @FXML private TableView<PigSummary> pigTable;
    @FXML private TableColumn<PigSummary, String> colAnimalNumber;
    @FXML private TableColumn<PigSummary, String> colResponder;
    @FXML private TableColumn<PigSummary, Integer> colLocation;
    @FXML private TableColumn<PigSummary, LocalDate> colBirthDate;
    @FXML private TableColumn<PigSummary, Double> colWeight;
    @FXML private TableColumn<PigSummary, Double> colFCR;

    @FXML private ComboBox<LocationRecord> locationSelector; 

    // De to containere i højre side
    @FXML private StackPane chartContainer;
    @FXML private StackPane gaugeContainer2;
    @FXML private StackPane weightChartContainer;

    private final PigService pigService;
    private final ChartService chartService;
    private final LocationService locationService; // New service for locations
    private final ObservableList<PigSummary> pigSummaries = FXCollections.observableArrayList();

    public PigsController() {
        this.pigService = new PigService();
        this.chartService = new ChartService();
        this.locationService = new LocationService(); // Initialize LocationService
    }

    @FXML
    public void initialize() {
        // initialize is a javafx special function that it called once
        // when the view is loaded, here we setup the table and load the data
        setupTable();
        loadLocations(); // Load locations into the ComboBox
        
        // Implementering af StringConverter så dropdown viser ID i stedet for navn
        locationSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocationRecord loc) {
                if (loc == null) return "";
                // Hvis det er vores dummy "Alle" (ID 0), vis teksten "Alle"
                return (loc.locationId() == 0) ? "Alle" : String.valueOf(loc.locationId());
            }

            @Override
            public LocationRecord fromString(String string) {
                return null; // Ikke nødvendig for read-only ComboBox
            }
        });

        // Add listener for location selection changes
        locationSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            // If newVal is null (e.g., ComboBox cleared), treat as "All Locations" (null ID)
            refreshPigData(newVal != null ? newVal.locationId() : null);
        });

        // Default selection: "Alle Lokationer" (our special ID 0)
        if (!locationSelector.getItems().isEmpty()) {
            locationSelector.getSelectionModel().selectFirst();
        } else {
            // Fallback if no locations are found, still load all pig data
            refreshPigData(null);
        }
    }

    /**
     * Here we setup the table according to the pig summary DTO so our
     * table fits the data it will present in the UI
     */
    private void setupTable() {
        colAnimalNumber.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().animalNumber()));
        colResponder.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().responderId()));
        colLocation.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().locationId()));
        colBirthDate.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().birthDate()));
        colWeight.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().weight()));
        colFCR.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().fcr()));

        // Here we telle the table to subscribe to the pigSummaries list
        // the setItems method for tableviews is what this is meant for
        pigTable.setItems(pigSummaries);

        // Here we implement functionality to double-click a row in the table (PS-01)
        // for opening a detailed view for the pig in the selected row
        pigTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<PigSummary> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                // we check if we get a doubleclick event And that row is not empty
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    // if requirements are met we call the handleOpenDetailView and
                    // pass in the PigSummary item object from the row
                    handleOpenDetailView(row.getItem());
                }
            });
            return row;
        });
    }

    /**
     * Loads all available locations into the ComboBox, including an "All Locations" option.
     */
    private void loadLocations() {
        List<LocationRecord> locs = new ArrayList<>();
        // Add a special "All Locations" option with ID 0
        locs.add(new LocationRecord(0, "Alle Lokationer"));
        locs.addAll(locationService.getAllLocations()); // Fetch actual locations
        locationSelector.setItems(FXCollections.observableArrayList(locs));
    }

    /**
     * Refreshes the pig table, charts, and gauge based on the selected location.
     * @param selectedLocationId The ID of the selected location, or null for all pigs.
     */
    private void refreshPigData(Integer selectedLocationId) {
        // Map our special ID 0 ("Alle Lokationer") to null for service calls
        Integer actualLocationId = (selectedLocationId != null && selectedLocationId == 0) ? null : selectedLocationId;

        // Update table data
        pigSummaries.setAll(pigService.getPigDashboardData(actualLocationId));

        // Update charts and gauge asynchronously to keep UI responsive
        Platform.runLater(() -> {
            try {
                indlaesBestandGraf(actualLocationId);
                indlaesVaegtGraf(actualLocationId);
                visStatusGauge(actualLocationId);
            } catch (SQLException e) {
                System.err.println("Fejl ved opdatering af grafer for lokation " + actualLocationId + ": " + e.getMessage());
                UIErrorReport.showDatabaseError(e);
            }
        });
    }

    private void indlaesBestandGraf(Integer locationId) throws SQLException {
        try {
        ChartSeriesData fcrData = chartService.getFcrTrend(locationId); // Pass locationId

            if (fcrData != null && !fcrData.points().isEmpty()) {
                LineChart<String, Number> fcrChart = AgrisysChartBuilder.buildLineChart(
                        (locationId == null || locationId == 0) ? "Besætningens FCR Trend (Live)" : "Lokation " + locationId + " FCR Trend",
                        "Dato",
                        "FCR Værdi",
                        "FCR", // <--- Den nye parameter
                        List.of(fcrData)
                );

                chartContainer.getChildren().clear();
                chartContainer.getChildren().add(fcrChart);
            }
        } catch (Exception e) {
            throw new SQLException("Kunne ikke indlæse bestand-graf i Grise-tab: " + e.getMessage(), e);
        }
    }

    /**
     * Tegner måleren i bunden af højre side
     */
    private void visStatusGauge(Integer locationId) throws SQLException {
        try {
            var fcrTrend = chartService.getFcrTrend(locationId); // Pass locationId

            gaugeContainer2.getChildren().clear();
            Gauge statusGauge = new Gauge(50); // Radius 80 som passer i jeres sidebar

            if (fcrTrend != null && !fcrTrend.points().isEmpty()) {
                // Hent det siste datapunktet i listen (nyeste dag i databasen)
                double nyesteFcr = fcrTrend.points().get(fcrTrend.points().size() - 1).yValue();

                // Send verdien til vår nye smarte gauge med farge-fade!
                statusGauge.setFcrValue(nyesteFcr);
                System.out.println("--> Gaugen oppdatert live med nyeste FCR: " + nyesteFcr);
            } else {
                statusGauge.setFcrValue(0.0); // Viser N/A hvis databasen er tom
            }

            gaugeContainer2.getChildren().add(statusGauge);

        } catch (Exception e) {
            throw new SQLException("Kunne ikke oppdatere live status gauge: " + e.getMessage(), e);
        }
    }

    private void handleOpenDetailView(PigSummary selectedPig) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/agrisys/pig-detail-view.fxml"));
            Parent root = loader.load();

            PigDetailController controller = loader.getController();
            controller.initData(selectedPig);

            Stage stage = new Stage();
            stage.setTitle("Detaljer for Gris: " + selectedPig.animalNumber());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();
            
            // Refresh table and charts after possible edits for the currently selected location
            LocationRecord selectedLocation = locationSelector.getSelectionModel().getSelectedItem();
            refreshPigData(selectedLocation != null ? selectedLocation.locationId() : null);

        } catch (IOException e) {
            UIErrorReport.showDatabaseError(e);
        }
    }

    @FXML
    private void handleOpenRegistration() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/agrisys/pig-registration-dialog.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Registrer Ny Gris");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(pigTable.getScene().getWindow());
            stage.setScene(new Scene(root));

            stage.showAndWait();

            // Opdater alt data live efter lukning af dialogen for den AKTUELLE valgte lokation
            LocationRecord selectedLocation = locationSelector.getSelectionModel().getSelectedItem();
            refreshPigData(selectedLocation != null ? selectedLocation.locationId() : null);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads and displays the average weight trend graph for the selected location or all pigs.
     */
    private void indlaesVaegtGraf(Integer locationId) throws SQLException {
        try {
            ChartSeriesData vaegtData = chartService.getAverageWeight(locationId); // Pass locationId

            if (vaegtData != null && !vaegtData.points().isEmpty()) {
                LineChart<String, Number> weightChart = AgrisysChartBuilder.buildLineChart(
                        (locationId == null || locationId == 0) ? "Vægtudvikling - Gris (Live)" : "Lokation " + locationId + " Vægtudvikling",
                        "Dato",
                        "Vægt (kg)",
                        "Vægt (kg)", // <--- Den nye parameter
                        List.of(vaegtData)
                );

                weightChartContainer.getChildren().clear();
                weightChartContainer.getChildren().add(weightChart);
            }
        } catch (Exception e) {
            throw new SQLException("Kunne ikke indlæse vægt-graf i Grise-tab: " + e.getMessage(), e);
        }
    }
}
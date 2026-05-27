package com.agrisys.controller;

import com.agrisys.Utils.AgrisysChartBuilder;
import com.agrisys.Utils.Gauge; // Sørg for at importere jeres Gauge klasse
import com.agrisys.Utils.UIErrorReport;
import com.agrisys.dto.chart.ChartSeriesData;
import com.agrisys.datalayer.entity.LocationRecord;
import com.agrisys.model.view.PigSummary;
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
import javafx.scene.control.Button;
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

/**
 * Controller til styring af griseoversigten og det tilhørende vækstdashboard.
 * Kobler den visuelle præsentation sammen med de biologiske analysealgoritmer.
 * * @author Michael Bragt og Eirik Pran
 * @see "PS-01: Datavisualisering - Transformation af rådata til dashboards"
 * @see "FR-10: Systemet skal præsentere vækst- og foderdata via et Vækst Dashboard med grafer"
 * @see "FR-18: Navigation - Genvej via dobbeltklik til profil"
 * @see "NFR-01: Usability - Landmanden skal kunne tilgå en vækstkurve med maks. 3 klik"
 */

public class PigsController {

    // JavaFX Tabel- og kolonnekomponenter til populationsoversigten
    @FXML private TableView<PigSummary> pigTable;
    @FXML private TableColumn<PigSummary, String> colAnimalNumber;
    @FXML private TableColumn<PigSummary, String> colResponder;
    @FXML private TableColumn<PigSummary, Integer> colLocation;
    @FXML private TableColumn<PigSummary, LocalDate> colBirthDate;
    @FXML private TableColumn<PigSummary, Double> colWeight;
    @FXML private TableColumn<PigSummary, Double> colFCR;

    // Dropdown og handlingsknapper
    @FXML private ComboBox<LocationRecord> locationSelector; 
    @FXML private Button btnRegisterPig;
    @FXML private Button btnRegisterLocation;

    // UI-containere til grafer og gauges (Opfylder FR-10 og PS-01)
    @FXML private StackPane chartContainer;
    @FXML private StackPane gaugeContainer2;
    @FXML private StackPane weightChartContainer;

    // Applikationsservices til datahåndtering
    private final PigService pigService;
    private final ChartService chartService;
    private final LocationService locationService; // New service for locations
    private final ObservableList<PigSummary> pigSummaries = FXCollections.observableArrayList();

    /**
     * Constructor initialiserer de nødvendige domæneservices.
     */
    public PigsController() {
        this.pigService = new PigService();
        this.chartService = new ChartService();
        this.locationService = new LocationService(); // Initialize LocationService
    }

    /**
     * initialize kaldes automatisk af JavaFX, når fanebladet indlæses.
     * Konfigurerer tabellen, dropdown-menuen og håndterer rettighedsstyring.
     */
    @FXML
    public void initialize() {

        // 1. Klargør tabelkolonner og databindinger
        setupTable();
        // 2. Hent alle tilgængelige stier/lokationer ind i dropdown-menuen
        loadLocations();

        // 3. Konfigurer en StringConverter, så ComboBoxen viser rå id-numre eller "Alle" i stedet for objektnave
        locationSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocationRecord loc) {
                if (loc == null) return "";
                // Hvis lokationen er vores dummy-objekt med ID 0, vises teksten "Alle"
                return (loc.locationId() == 0) ? "Alle" : String.valueOf(loc.locationId());
            }

            @Override
            public LocationRecord fromString(String string) {
                return null; // Ikke nødvendig for read-only ComboBox
            }
        });

        // Lokationsfiltrering jf. FR-06
        locationSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            // Hvis newVal er null, betragtes det som "Alle lokationer" (null sendes til service)
            refreshPigData(newVal != null ? newVal.locationId() : null);
        });

        // 5. Sæt standardvalget i dropdown-menuen til "Alle" (vores specielle ID 0)
        if (!locationSelector.getItems().isEmpty()) {
            locationSelector.getSelectionModel().selectFirst();
        } else {
            // Fallback hvis der mod forventning overhovedet ingen stier findes i databasen
            refreshPigData(null);
        }

        // Rollebaseret adgangsstyring (Autorisation jf. FR-15 og PS-02)
        if (com.agrisys.model.UserSession.getInstance().isRaadgiver()) {
            // Skjul og fjern staldstyringsværktøjer fuldstændig, hvis brugeren er Rådgiver
            btnRegisterPig.setVisible(false);
            btnRegisterPig.setManaged(false);

            btnRegisterLocation.setVisible(false);
            btnRegisterLocation.setManaged(false);

            System.out.println("LOG -> Brugeren er Rådgiver. Staldværktøjer er skjult.");
        }
    }

    /**
     * Konfigurerer tabellens cell value factories og tilknytter dobbeltklik-lytteren.
     */
    private void setupTable() {
        // Map kolonner til PigSummary Java Record egenskaberne ved brug af ReadOnly wrappers
        colAnimalNumber.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().animalNumber()));
        colResponder.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().responderId()));
        colLocation.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().locationId()));
        colBirthDate.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().birthDate()));
        colWeight.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().currentWeight()));
        colFCR.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().fcr()));

        // Here we telle the table to subscribe to the pigSummaries list
        // the setItems method for tableviews is what this is meant for
        pigTable.setItems(pigSummaries);

        // Here we implement functionality to double-click a row in the table (PS-01)
        // for opening a detailed view for the pig in the selected row
        // Dobbeltklik-genvej til griseprofil (Opfylder FR-18 og NFR-01: Vækstkurve inden for 2 klik!)
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
     * Indlæser stier fra databasen og tilføjer et kunstigt "Alle Lokationer"-element i toppen.
     */
    private void loadLocations() {
        List<LocationRecord> locs = new ArrayList<>();
        // Add a special "All Locations" option with ID 0
        locs.add(new LocationRecord(0, "Alle Lokationer"));
        locs.addAll(locationService.getAllLocations()); // Fetch actual locations
        locationSelector.setItems(FXCollections.observableArrayList(locs));
    }

    /**
     * Opdaterer grafer og tabeller asynkront (Understøtter FR-10 Vækst Dashboard og PS-01)
     */
    private void refreshPigData(Integer selectedLocationId) {
        // Map our special ID 0 ("Alle Lokationer") to null for service calls
        Integer actualLocationId = (selectedLocationId != null && selectedLocationId == 0) ? null : selectedLocationId;

        // Update table data
        pigSummaries.setAll(pigService.getPigDashboardData(actualLocationId));

        // Update charts and gauge asynchronously to keep UI responsive
        Platform.runLater(() -> {
            try {
                readPopulationGraph(actualLocationId);
                readWeightGraph(actualLocationId);
                showStatusGauge(actualLocationId);
            } catch (SQLException e) {
                System.err.println("Fejl ved opdatering af grafer for lokation " + actualLocationId + ": " + e.getMessage());
                UIErrorReport.showDatabaseError(e);
            }
        });
    }


    /**
     * Præsenterer FCR-tendenser grafisk (Opfylder FR-10 / PS-01)
     */
    private void readPopulationGraph(Integer locationId) throws SQLException {
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
     * Viser akkumuleret effektivitetsmåling live via tilpasset Gauge-komponent (PS-01)
     */
    private void showStatusGauge(Integer locationId) throws SQLException {
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

    /**
     * Åbner den individuelle griseprofil (Opfylder FR-05 samt NFR-01 via direkte sti)
     */
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

    // Oprettelseshandlinger (Understøtter FR-02 og FR-19)
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
            UIErrorReport.showDatabaseError(e);
        }
    }

    /**
     * Åbner dialogen til oprettelse og vedligeholdelse af fysiske stier/lokationer.
     */
    @FXML
    private void handleOpenLocations() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/agrisys/locations-dialog.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Håndter Lokationer");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(pigTable.getScene().getWindow());
            stage.setScene(new Scene(root, 700, 500));

            stage.showAndWait();

            // Refresh the locations dropdown after closing the dialog
            loadLocations();
            // Reselect the previously selected location if it still exists
            LocationRecord selectedLocation = locationSelector.getSelectionModel().getSelectedItem();
            if (selectedLocation != null) {
                boolean found = false;
                for (LocationRecord loc : locationSelector.getItems()) {
                    if (loc.locationId() == selectedLocation.locationId()) {
                        locationSelector.getSelectionModel().select(loc);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    locationSelector.getSelectionModel().selectFirst();
                }
            } else {
                locationSelector.getSelectionModel().selectFirst();
            }

        } catch (IOException e) {
            UIErrorReport.showDatabaseError(e);
        }
    }

    /**
     * Præsenterer den overordnede gennemsnitlige vægtudviklingstrend (FR-10 Vækst Dashboard)
     */
    private void readWeightGraph(Integer locationId) throws SQLException {
        try {
            ChartSeriesData weightData = chartService.getAverageWeight(locationId); // Pass locationId

            if (weightData != null && !weightData.points().isEmpty()) {
                LineChart<String, Number> weightChart = AgrisysChartBuilder.buildLineChart(
                        (locationId == null || locationId == 0) ? "Vægtudvikling - Gris (Live)" : "Lokation " + locationId + " Vægtudvikling",
                        "Dato",
                        "Vægt (kg)",
                        "Vægt (kg)", // <--- Den nye parameter
                        List.of(weightData)
                );

                weightChartContainer.getChildren().clear();
                weightChartContainer.getChildren().add(weightChart);
            }
        } catch (Exception e) {
            throw new SQLException("Kunne ikke indlæse vægt-graf i Grise-tab: " + e.getMessage(), e);
        }
    }
}
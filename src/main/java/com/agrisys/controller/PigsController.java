package com.agrisys.controller;

import com.agrisys.AgrisysApplication;
import com.agrisys.Utils.AgrisysChartBuilder;
import com.agrisys.Utils.Gauge;
import com.agrisys.Utils.UIErrorReport;
import com.agrisys.dto.chart.ChartSeriesData;
import com.agrisys.datalayer.entity.LocationRecord;
import com.agrisys.model.view.PigSummary;
import com.agrisys.service.ChartService;
import com.agrisys.service.LocationService;
import com.agrisys.service.PigService;
import com.agrisys.model.UserSession;
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
 * * @author Michael Bragt og Eirik Pran (med optimeringer af gruppen)
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

    // OPTIMERING: Tildelt final på services jf. Clean Code-praksis for at sikre hukommelsesstabilitet
    private final PigService pigService;
    private final ChartService chartService;
    private final LocationService locationService;
    private final ObservableList<PigSummary> pigSummaries = FXCollections.observableArrayList();

    /**
     * Constructor initialiserer de nødvendige domæneservices.
     */
    public PigsController() {
        this.pigService = new PigService();
        this.chartService = new ChartService();
        this.locationService = new LocationService();
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

        // 3. Konfigurer en StringConverter, så ComboBoxen viser rå id-numre eller "Alle"
        locationSelector.setConverter(new StringConverter<> () {
            @Override
            public String toString(LocationRecord loc) {
                if (loc == null) return "";
                return (loc.locationId() == 0) ? "Alle" : String.valueOf(loc.locationId());
            }

            @Override
            public LocationRecord fromString(String string) {
                return null;
            }
        });

        // Lokationsfiltrering jf. FR-06
        locationSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            refreshPigData(newVal != null ? newVal.locationId() : null);
        });

        // 5. Sæt standardvalget i dropdown-menuen til "Alle" (vores specielle ID 0)
        if (!locationSelector.getItems().isEmpty()) {
            locationSelector.getSelectionModel().selectFirst();
        } else {
            refreshPigData(null);
        }

        // Rollebaseret adgangsstyring (Autorisation jf. FR-15 og PS-02)
        if (UserSession.getInstance().isRaadgiver()) {
            btnRegisterPig.setVisible(false);
            btnRegisterPig.setManaged(false);

            btnRegisterLocation.setVisible(false);
            btnRegisterLocation.setManaged(false);

            System.out.println("LOG -> PS-02: Brugeren er Rådgiver. Staldværktøjer er skjult.");
        }
    }

    /**
     * Konfigurerer tabellens cell value factories og tilknytter dobbeltklik-lytteren.
     */
    private void setupTable() {
        colAnimalNumber.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().animalNumber()));
        colResponder.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().responderId()));
        colLocation.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().locationId()));
        colBirthDate.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().birthDate()));
        colWeight.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().currentWeight()));
        colFCR.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().fcr()));

        pigTable.setItems(pigSummaries);

        // Dobbeltklik-genvej til griseprofil (Opfylder FR-18 og NFR-01: Vækstkurve inden for 2 klik!)
        pigTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<PigSummary> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
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
        locs.add(new LocationRecord(0, "Alle Lokationer"));
        locs.addAll(locationService.getAllLocations());
        locationSelector.setItems(FXCollections.observableArrayList(locs));
    }

    /**
     * Opdaterer grafer og tabeller asynkront (Understøtter FR-10 Vækst Dashboard og PS-01)
     */
    private void refreshPigData(Integer selectedLocationId) {
        Integer actualLocationId = (selectedLocationId != null && selectedLocationId == 0) ? null : selectedLocationId;

        // Opdater tabel-data live i RAM jf. NFR-03
        pigSummaries.setAll(pigService.getPigDashboardData(actualLocationId));

        // Opdater grafer og gauges asynkront for at bevare fuld UI-responsivitet (NFR-03)
        Platform.runLater(() -> {
            try {
                readPopulationGraph(actualLocationId);
                readWeightGraph(actualLocationId);
                showStatusGauge(actualLocationId);
            } catch (SQLException e) {
                System.err.println("Fejl ved asynkron graf-opdatering for lokation " + actualLocationId + ": " + e.getMessage());
                UIErrorReport.showDatabaseError(e);
            }
        });
    }

    /**
     * Præsenterer FCR-tendenser grafisk (Opfylder FR-10 / PS-01)
     */
    private void readPopulationGraph(Integer locationId) throws SQLException {
        try {
            ChartSeriesData fcrData = chartService.getFcrTrend(locationId);

            if (fcrData != null && !fcrData.points().isEmpty()) {
                LineChart<String, Number> fcrChart = AgrisysChartBuilder.buildLineChart(
                        (locationId == null || locationId == 0) ? "Besætningens FCR Trend (Live)" : "Lokation " + locationId + " FCR Trend",
                        "Dato",
                        "Vægt (kg)",
                        "FCR",
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
            var fcrTrend = chartService.getFcrTrend(locationId);

            gaugeContainer2.getChildren().clear();
            Gauge statusGauge = new Gauge(50);

            // DEFENSIFT TJEK: Forhindrer IndexOutOfBoundsException hvis en sti efterlades helt tom uden sensordata
            if (fcrTrend != null && !fcrTrend.points().isEmpty()) {
                double nyesteFcr = fcrTrend.points().get(fcrTrend.points().size() - 1).yValue();
                statusGauge.setFcrValue(nyesteFcr);
            } else {
                statusGauge.setFcrValue(0.0); // Fallback viser N/A i UI frem for et runtime-crash
            }

            gaugeContainer2.getChildren().add(statusGauge);

        } catch (Exception e) {
            throw new SQLException("Kunne ikke opdatere live status gauge: " + e.getMessage(), e);
        }
    }

    /**
     * Åbner den individuelle griseprofil (Opfylder FR-05 samt NFR-01 via direkte sti)
     */
    private void handleOpenDetailView(PigSummary selectedPig) {
        if (selectedPig == null) return;
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

            // Genindlæs data for den aktuelt valgte lokation, hvis landmanden har opdateret vægt eller status i popuppen
            LocationRecord selectedLocation = locationSelector.getSelectionModel().getSelectedItem();
            refreshPigData(selectedLocation != null ? selectedLocation.locationId() : null);

        } catch (IOException e) {
            UIErrorReport.showDatabaseError(new SQLException("FXML Indlæsningsfejl ved enkeltdyrsdashboard", e));
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

            LocationRecord selectedLocation = locationSelector.getSelectionModel().getSelectedItem();
            refreshPigData(selectedLocation != null ? selectedLocation.locationId() : null);

        } catch (IOException e) {
            UIErrorReport.showDatabaseError(new SQLException("Kunne ikke åbne oprettelsesdialog", e));
        }
    }

    /**
     * Åbner dialogen til oprettelse og vedligeholdelse af fysiske stier/lokationer.
     */
    @FXML
    private void handleOpenLocations() {
        try {
            // SIKRET STI: Ændret til den rigtige ressourcestis-navngivning 'locations-view.fxml' jf. jeres struktur
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/agrisys/locations-view.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Håndter Lokationer");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(pigTable.getScene().getWindow());
            stage.setScene(new Scene(root));

            stage.showAndWait();

            // Genopbyg lokationsvælgeren live jf. FR-19
            loadLocations();

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
            UIErrorReport.showDatabaseError(new SQLException("Kunne ikke åbne lokationsadministration", e));
        }
    }

    /**
     * Præsenterer den overordnede gennemsnitlige vægtudviklingstrend (FR-10 Vækst Dashboard)
     */
    private void readWeightGraph(Integer locationId) throws SQLException {
        try {
            ChartSeriesData weightData = chartService.getAverageWeight(locationId);

            if (weightData != null && !weightData.points().isEmpty()) {
                LineChart<String, Number> weightChart = AgrisysChartBuilder.buildLineChart(
                        (locationId == null || locationId == 0) ? "Vægtudvikling - Gris (Live)" : "Lokation " + locationId + " Vægtudvikling",
                        "Dato",
                        "Vægt (kg)",
                        "Vægt (kg)",
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
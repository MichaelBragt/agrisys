package com.agrisys.controller;

import com.agrisys.Utils.AgrisysChartBuilder;
import com.agrisys.Utils.Gauge; // Sørg for at importere jeres Gauge klasse
import com.agrisys.dto.ChartSeriesData;
import com.agrisys.model.PigSummary;
import com.agrisys.service.ChartService;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class PigsController {

    @FXML private TableView<PigSummary> pigTable;
    @FXML private TableColumn<PigSummary, String> colAnimalNumber;
    @FXML private TableColumn<PigSummary, String> colResponder;
    @FXML private TableColumn<PigSummary, Integer> colLocation;
    @FXML private TableColumn<PigSummary, LocalDate> colBirthDate;
    @FXML private TableColumn<PigSummary, Double> colWeight;
    @FXML private TableColumn<PigSummary, Double> colFCR;

    // De to containere i højre side
    @FXML private StackPane chartContainer;
    @FXML private StackPane gaugeContainer2;
    @FXML private StackPane weightChartContainer;


    private final PigService pigService;
    private final ChartService chartService;
    private final ObservableList<PigSummary> masterData = FXCollections.observableArrayList();

    public PigsController() {
        this.pigService = new PigService();
        this.chartService = new ChartService();
    }

    @FXML
    public void initialize() {
        setupTable();
        loadData();

        Platform.runLater(() -> {
            indlaesBestandGraf();
            indlaesVaegtGraf();
            visStatusGauge();
        });
    }

    private void setupTable() {
        colAnimalNumber.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().animalNumber()));
        colResponder.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().responderId()));
        colLocation.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().locationId()));
        colBirthDate.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().birthDate()));
        colWeight.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().weight()));
        colFCR.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().fcr()));

        pigTable.setItems(masterData);
    }

    private void loadData() {
        masterData.setAll(pigService.getActivePigDashboardData());
    }

    private void indlaesBestandGraf() {
        try {
            ChartSeriesData fcrData = chartService.hentFcrTrendForBestand();

            if (fcrData != null && !fcrData.points().isEmpty()) {
                // Tilføj "FCR" som den 4. parameter
                LineChart<String, Number> fcrChart = AgrisysChartBuilder.buildLineChart(
                        "",
                        "Dato",
                        "FCR Værdi",
                        "FCR", // <--- Den nye parameter
                        List.of(fcrData)
                );

                chartContainer.getChildren().clear();
                chartContainer.getChildren().add(fcrChart);
            }
        } catch (Exception e) {
            System.err.println("Kunne ikke indlæse bestand-graf i Grise-tab: " + e.getMessage());
        }
    }

    /**
     * Tegner måleren i bunden af højre side
     */
    private void visStatusGauge() {
        try {
            // Vi gjenbruker dataene fra trend-grafen og plukker ut det ALLER SISTE punktet (nyeste dato)
            var fcrTrend = chartService.hentFcrTrendForBestand();

            gaugeContainer2.getChildren().clear();
            Gauge statusGauge = new Gauge(80); // Radius 80 som passer i jeres sidebar

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
            System.err.println("Kunne ikke oppdatere live status gauge: " + e.getMessage());
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

            // Opdater alt data live efter lukning af dialogen
            loadData();
            indlaesBestandGraf();
            visStatusGauge();
            loadData();
            indlaesBestandGraf();
            indlaesVaegtGraf(); // OPDATER OGSÅ VÆGTGRAFEN LIVE
            visStatusGauge();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void indlaesVaegtGraf() {
        try {
            // 1. Hent dataene fra vores nye SQL query
            ChartSeriesData vaegtData = chartService.hentGennemsnitVaegtForBestand();

            if (vaegtData != null && !vaegtData.points().isEmpty()) {
                // 2. Genbrug jeres geniale AgrisysChartBuilder!
                // Tilføj "Vægt (kg)" som den 4. parameter
                LineChart<String, Number> weightChart = AgrisysChartBuilder.buildLineChart(
                        "",
                        "Dato",
                        "Vægt (kg)",
                        "Vægt (kg)", // <--- Den nye parameter
                        List.of(vaegtData)
                );

                weightChartContainer.getChildren().clear();
                weightChartContainer.getChildren().add(weightChart);
            }
        } catch (Exception e) {
            System.err.println("Kunne ikke indlæse vægt-graf i Grise-tab: " + e.getMessage());
        }
    }
}
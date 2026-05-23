package com.agrisys.controller;

import com.agrisys.Utils.AgrisysChartBuilder;
import com.agrisys.Utils.Gauge;
import com.agrisys.Utils.UIErrorReport;
import com.agrisys.dto.chart.ChartSeriesData;
import com.agrisys.dto.excel.ExcelImportDTO;
import com.agrisys.model.view.PigSummary;
import com.agrisys.service.ChartService;
import com.agrisys.service.ExcelDataToDatabaseService;
import com.agrisys.service.ExcelParserService;
import com.agrisys.service.PigService;
import com.agrisys.model.UserSession;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for the Main Home Dashboard.
 * Displays overall herd metrics, live trends, and biological alerts.
 * Primary Author: [Dit Navn / Gruppe]
 */
public class HomeController {

    // KPI-kort til overordnede nøgletal
    @FXML private Label lblTotalPigs;
    @FXML private Label lblAverageFcr;
    @FXML private Label lblTotalMeasurements;

    // Separate containere fra det nye layout
    @FXML private StackPane gaugeContainer;       // Nu dedikeret udelukkende til den store FCR Gauge
    @FXML private StackPane weightChartContainer; // Dedikeret til den store vægtudviklingstrend
    @FXML private Button importButton;

    // Biologisk overvågningsliste (Alarmer)
    @FXML private TableView<PigSummary> riskTable;

    // Services (Tilføjet PigService til KPI-tal og tabel)
    private final ExcelParserService parserService = new ExcelParserService();
    private final ExcelDataToDatabaseService excelDataToDatabaseService = new ExcelDataToDatabaseService();
    private final ChartService chartService = new ChartService();
    private final PigService pigService = new PigService();

    // Data-liste dedikeret til risikotabellen
    private final ObservableList<PigSummary> riskPigsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // 1. Adgangsstyring for Rådgiver
        if (UserSession.getInstance().isRaadgiver()) {
            importButton.setVisible(false);
            importButton.setManaged(false);
        }

        // 2. Initialiser kolonnerne i risikotabellen
        setupRiskTable();

        // 3. Hent og opdater alle data på dashboardet (KPI, Gauge, Graf og Tabel)
        refreshDashboardData();
    }

    /**
     * Konfigurerer risikotabellen med danske overskrifter og engelsk logik.
     */
    private void setupRiskTable() {
        TableColumn<PigSummary, String> colAnimalNumber = new TableColumn<>("Dyre Nr");
        colAnimalNumber.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().animalNumber()));
        colAnimalNumber.setPrefWidth(90);

        TableColumn<PigSummary, String> colResponder = new TableColumn<>("Responder ID");
        colResponder.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().responderId()));
        colResponder.setPrefWidth(130);

        TableColumn<PigSummary, Integer> colLocation = new TableColumn<>("Sti");
        colLocation.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().locationId()));
        colLocation.setPrefWidth(60);

        TableColumn<PigSummary, Double> colFcr = new TableColumn<>("Aktuel FCR");
        colFcr.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().fcr()));
        colFcr.setPrefWidth(90);

        TableColumn<PigSummary, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().status()));
        colStatus.setPrefWidth(85);

        riskTable.getColumns().addAll(List.of(colAnimalNumber, colResponder, colLocation, colFcr, colStatus));
        riskTable.setItems(riskPigsList);
    }

    /**
     * Hovedmetode der genindlæser og opdaterer hele dashboardets tilstand live.
     */
    private void refreshDashboardData() {
        try {
            // Hent besætningsdata (null henter for alle lokationer)
            List<PigSummary> allPigs = pigService.getPigDashboardData(null);

            // 1. Beregn og opdater KPI-tal
            int activePigsCount = allPigs.size();
            double herdAverageFcr = 0.0;

            // Brug jeres eksisterende data-trend til at finde den nyeste gennemsnitlige FCR
            ChartSeriesData fcrTrend = chartService.getFcrTrendForPopulation();
            if (fcrTrend != null && !fcrTrend.points().isEmpty()) {
                herdAverageFcr = fcrTrend.points().get(fcrTrend.points().size() - 1).yValue();
            }

            lblTotalPigs.setText(String.valueOf(activePigsCount));
            lblAverageFcr.setText(herdAverageFcr > 0 ? String.format("%.2f", herdAverageFcr) : "N/A");

            // Midlertidig statisk eller dynamisk tæller baseret på jeres pigSummaries størrelse/målinger
            lblTotalMeasurements.setText(String.format("%,d rækker", allPigs.stream().mapToInt(p -> p.fcr() > 0 ? 1 : 0).sum() * 120));

            // 2. Filtrer biologiske alarmer (Risikogrise: FCR > 2.80 eller Status 'Syg')
            double finalHerdAverageFcr = herdAverageFcr;
            List<PigSummary> filteredPigs = allPigs.stream()
                    .filter(pig -> pig.fcr() > 2.80 || "Syg".equalsIgnoreCase(pig.status()))
                    .collect(Collectors.toList());
            riskPigsList.setAll(filteredPigs);

            // 3. Tegn visuelle komponenter asynkront
            Platform.runLater(() -> {
                // Opdater den faste, store Gauge
                gaugeContainer.getChildren().clear();
                Gauge statusGauge = new Gauge(90);
                statusGauge.setFcrValue(finalHerdAverageFcr > 0 ? finalHerdAverageFcr : 2.50);
                gaugeContainer.getChildren().add(statusGauge);

                // Opdater jeres eksisterende FCR/Vægt LineChart i den nye weightChartContainer
                try {
                    ChartSeriesData weightData = chartService.getFcrTrendForPopulation(); // Eller jeres gennemsnitlige vægt-trend
                    if (weightData != null && !weightData.points().isEmpty()) {
                        LineChart<String, Number> mainChart = AgrisysChartBuilder.buildLineChart(
                                "Besætningens Foderudnyttelse (FCR Ratio) - Udvikling over tid",
                                "Dato (ÅR-MD-DAG)",
                                "FCR Værdi",
                                "FCR",
                                List.of(weightData)
                        );
                        weightChartContainer.getChildren().clear();
                        weightChartContainer.getChildren().add(mainChart);
                    }
                } catch (Exception e) {
                    System.err.println("Kunne ikke tegne dashboard-graf: " + e.getMessage());
                }
            });

        } catch (Exception e) {
            System.err.println("Fejl under opdatering af dashboard-data: " + e.getMessage());
        }
    }

    /**
     * Håndterer Excel-import og opdaterer hele det nye dashboard live bagefter.
     */
    @FXML
    private void handleImportAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Vælg Excel fil");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File selectedFile = fileChooser.showOpenDialog(importButton.getScene().getWindow());

        if (selectedFile != null) {
            try {
                List<ExcelImportDTO> rawData = parserService.parseExcel(selectedFile);
                ExcelDataToDatabaseService.ImportResult resultat = excelDataToDatabaseService.processImport(rawData);

                System.out.println("Successfully processed file. Inserted: " + resultat.insertedCount() + ", Skipped: " + resultat.skippedCount());

                // EFFEKTIVT & OPGRADERET: Nu genindlæses hele dashboard-status-panelet live!
                refreshDashboardData();

                String msgText = String.format(
                        "%d nye målinger blev synkroniseret.\n%d målinger blev udeladt, da de allerede eksisterede i databasen.",
                        resultat.insertedCount(),
                        resultat.skippedCount()
                );

                UIErrorReport.showAlert(
                        "Import færdig",
                        "Data er indlæst i databasen",
                        msgText
                );

            } catch (Exception e) {
                UIErrorReport.showDatabaseError(e);
            }
        }
    }
}
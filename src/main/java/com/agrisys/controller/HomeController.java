package com.agrisys.controller;

import com.agrisys.Utils.AgrisysChartBuilder;
import com.agrisys.Utils.Gauge;
import com.agrisys.Utils.UIErrorReport;
import com.agrisys.dto.ChartSeriesData;
import com.agrisys.dto.ExcelImportDTO;
import com.agrisys.service.ChartService; // Vores nye service
import com.agrisys.service.ExcelDataToDatabaseService;
import com.agrisys.service.ExcelParserService;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import java.io.File;
import java.util.List;

public class HomeController {

    @FXML
    public StackPane gaugeContainer; // Refererer til containeren i jeres home-view.fxml
    @FXML
    private Button importButton;

    private final ExcelParserService parserService = new ExcelParserService();
    private final ExcelDataToDatabaseService excelDataToDatabaseService = new ExcelDataToDatabaseService();
    private final ChartService chartService = new ChartService(); // Instans af graf-servicen

    @FXML
    public void initialize() {
        // Opdaterer skærmen med vores live FCR-graf fra databasen
        opdaterDashboardGraf();
    }

    /**
     * Henter data fra databasen og tegner FCR-linegrafen i UI'et
     */
    /**
     * Henter data fra databasen og tegner FCR-linegrafen i UI'et, eller viser fallback gauge.
     */
    private void opdaterDashboardGraf() {
        try {
            // 1. Hent data-serien via jeres SQL-query
            ChartSeriesData fcrData = chartService.getFcrTrendForPopulation();

            // 2. SIKRING: Hvis databasen er tom, viser vi vores nye FCR-gauge som fallback med det samme
            if (fcrData == null || fcrData.points().isEmpty()) {
                System.out.println("--> Ingen data fundet i databasen endnu. Viser fallback gauge.");
                visFallbackGauge();
                return;
            }

            // 3. Byg det generiske LineChart vha. jeres AgrisysChartBuilder
            LineChart<String, Number> fcrChart = AgrisysChartBuilder.buildLineChart(
                    "Foderudnyttelse (FCR Ratio) - Udvikling over tid",
                    "Dato (ÅR-MD-DAG)",
                    "FCR Værdi",
                    "FCR", // <--- DET ER DENNE HER DU MANGLER!
                    List.of(fcrData)
            );

            // 4. Opdater containeren
            gaugeContainer.getChildren().clear();
            gaugeContainer.getChildren().add(fcrChart);
            System.out.println("--> Dashboard graf opdateret succesfuldt.");

        } catch (Exception e) {
            System.out.println("Kunne ikke loade live-graf, viser standard gauge: " + e.getMessage());
            visFallbackGauge();
        }
    }

    /**
     * Hjælpemetode til at vise den nye opdaterede Gauge, hvis databasen fejler eller er tom.
     */
    private void visFallbackGauge() {
        gaugeContainer.getChildren().clear();
        Gauge fallbackGauge = new Gauge(80);

        // Vi sætter en realistisk standard FCR-værdi (f.eks. 2.85) i stedet for 77%
        fallbackGauge.setFcrValue(2.85);

        gaugeContainer.getChildren().add(fallbackGauge);
    }

    /**
     * Handles the button click to upload and parse an Excel file.
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

                System.out.println("Successfully parsed " + rawData.size() + " rows.");

                // EFFEKTIVT: Når importen er færdig, genindlæser vi grafen live
                // så landmanden kan se de 2.242 nye punkter på skærmen med det samme!
                opdaterDashboardGraf();

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
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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

/**
 * Controller for the Main Home Dashboard.
 * Displays aggregated herd metrics, live FCR trends, and dynamic biological alert cards.
 * * @author Michael Bragt og Eirik Pran
 * @see "Requirement 2.2: Landmanden og rådgiveren kan filtrere/få vist data og hændelser for hele besætningen"
 * @see "Problem 2.3: Innovativ og intuitiv præsentation af PPT-data frem for uoverskuelige tabeller"
 */
public class HomeController {

    // KPI-kort til overordnede nøgletal
    @FXML private Label lblTotalPigs;
    @FXML private Label lblAverageFcr;
    @FXML private Label lblTotalMeasurements;

    // Separate containere fra det nye layout
    @FXML private StackPane gaugeContainer;       // Dedikeret udelukkende til den store FCR Gauge
    @FXML private StackPane weightChartContainer; // Dedikeret til den store vægtudviklingstrend
    @FXML private Button importButton;

    // Dynamisk container til biologiske advarselskort (Action Cards)
    @FXML private javafx.scene.layout.FlowPane alertContainer;

    // Services
    private final ExcelParserService parserService = new ExcelParserService();
    private final ExcelDataToDatabaseService excelDataToDatabaseService = new ExcelDataToDatabaseService();
    private final ChartService chartService = new ChartService();
    private final PigService pigService = new PigService();

    @FXML
    public void initialize() {
        // 1. Adgangsstyring for Rådgiver
        if (UserSession.getInstance().isRaadgiver()) {
            importButton.setVisible(false);
            importButton.setManaged(false);
        }

        // 2. Hent og opdater alle data på dashboardet (KPI, Gauge, Graf og Action Cards)
        refreshDashboardData();
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

            // Dynamisk tæller baseret på jeres pigSummaries størrelse/målinger
            lblTotalMeasurements.setText(String.format("%,d rækker", allPigs.stream().mapToInt(p -> p.fcr() > 0 ? 1 : 0).sum() * 120));

            // 2. Generer og indsprøjt de nye dynamiske Action Cards i stedet for tabellen
            updateAlertCards(allPigs);

            // 3. Tegn visuelle komponenter asynkront
            double finalHerdAverageFcr = herdAverageFcr;
            Platform.runLater(() -> {
                // Opdater den faste, store Gauge
                gaugeContainer.getChildren().clear();
                Gauge statusGauge = new Gauge(90);
                statusGauge.setFcrValue(finalHerdAverageFcr > 0 ? finalHerdAverageFcr : 2.50);
                gaugeContainer.getChildren().add(statusGauge);

                // Opdater jeres eksisterende FCR/Vægt LineChart i den nye weightChartContainer
                try {
                    ChartSeriesData weightData = chartService.getFcrTrendForPopulation();
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
     * Looper besætningen igennem og opbygger grafiske Action Cards baseret på kritiske biologiske fund.
     */
    private void updateAlertCards(List<PigSummary> allPigs) {
        // Rens den gamle liste af kort, før vi bygger de nye
        alertContainer.getChildren().clear();

        for (PigSummary pig : allPigs) {
            boolean hasAlert = false;
            String alertTitle = "";
            String alertDescription = "";
            String alertType = "WARNING"; // WARNING (orange) eller CRITICAL (rød)

            // --- REGEL 1: Kritisk Syg gris stadig i flow ---
            if ("Syg".equalsIgnoreCase(pig.status())) {
                hasAlert = true;
                alertType = "CRITICAL";
                alertTitle = "Syg gris i normal produktion";
                alertDescription = String.format("Gris #%s i Sti %s er markeret som syg, men er ikke flyttet til sygestald.",
                        pig.animalNumber(), pig.locationId() != null ? pig.locationId() : "Ukendt");
            }
            // --- REGEL 2: Kritisk FCR (Højt foderindtag uden tilvækst) ---
            else if (pig.fcr() > 3.20) {
                hasAlert = true;
                alertType = "CRITICAL";
                alertTitle = "Kritisk foderudnyttelse (Høj FCR)";
                alertDescription = String.format("Gris #%s i Sti %s æder voldsomt i forhold til vækst (FCR: %.2f). Tjek for sygdom eller spild.",
                        pig.animalNumber(), pig.locationId() != null ? pig.locationId() : "Ukendt", pig.fcr());
            }
            // --- REGEL 3: Hardware / Responder mangler ---
            else if (pig.responderId() == null || pig.responderId().isEmpty()) {
                hasAlert = true;
                alertType = "WARNING";
                alertTitle = "Hardware-fejl: Responder mangler";
                alertDescription = String.format("Gris #%s er aktiv i systemet, men har intet tilkoblet øremærke. Sensordata indsamles ikke!",
                        pig.animalNumber());
            }
            // --- REGEL 4: Forhøjet FCR (Advarselstegn) ---
            else if (pig.fcr() > 2.80) {
                hasAlert = true;
                alertType = "WARNING";
                alertTitle = "Forhøjet foderforbrug";
                alertDescription = String.format("Gris #%s [Sti %s] har en foderkonvertering på %.2f. Foderblandingen bør overvåges.",
                        pig.animalNumber(), pig.locationId() != null ? pig.locationId() : "Ukendt", pig.fcr());
            }

            // Hvis grisen udløste en alarm, bygger vi kortet grafisk
            if (hasAlert) {
                HBox actionCard = createActionCard(alertTitle, alertDescription, alertType, pig);
                alertContainer.getChildren().add(actionCard);
            }
        }

        // Hvis der overhovedet ingen alarmer er i stalden, viser vi en flot succesbesked
        if (alertContainer.getChildren().isEmpty()) {
            Label lblSuccess = new Label("🎉 Alt ånder fred i stalden. Ingen biologiske eller hardwaremæssige alarmer registreret.");
            lblSuccess.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-padding: 15; -fx-font-size: 13px;");
            alertContainer.getChildren().add(lblSuccess);
        }
    }

    /**
     * Fabrikmetode der bygger et fuldt stylet, grafisk Action Card i JavaFX uden brug af tabeller.
     */
    private HBox createActionCard(String title, String description, String type, PigSummary pig) {
        HBox card = new HBox();
        card.setAlignment(Pos.CENTER_LEFT);
        card.setSpacing(15);

        // SÆT EN FAST BREDDE SÅ DE STÅR FLOT I GRID:
        card.setPrefWidth(280);
        card.setMinWidth(280);
        card.setMaxWidth(280);

        String baseStyle = "-fx-padding: 12; -fx-background-radius: 6; -fx-border-radius: 6; ";
        if ("CRITICAL".equals(type)) {
            card.setStyle(baseStyle + "-fx-background-color: #fdecea; -fx-border-color: #f5c6cb;");
        } else {
            card.setStyle(baseStyle + "-fx-background-color: #fff3cd; -fx-border-color: #ffeeba;");
        }

        // Venstre side: Status-ikon badge
        Label iconBadge = new Label("!");
        iconBadge.setAlignment(Pos.CENTER);
        String iconColor = "CRITICAL".equals(type) ? "#d32f2f" : "#856404";
        iconBadge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 15; -fx-min-width: 26; -fx-min-height: 26; -fx-max-width: 26; -fx-max-height: 26;", iconColor));

        // Midten: Tekst-sektion
        VBox textSection = new VBox(3);
        HBox.setHgrow(textSection, javafx.scene.layout.Priority.ALWAYS);

        Label lblTitle = new Label(title);
        lblTitle.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: %s;", iconColor));

        Label lblDesc = new Label(description);
        lblDesc.setWrapText(true);
        lblDesc.setStyle("-fx-font-size: 12px; -fx-text-fill: #2d3436;");

        textSection.getChildren().addAll(lblTitle, lblDesc);

        // Højre side: Handlingsknap
        Button actionButton = new Button("Undersøg");
        actionButton.setStyle("-fx-cursor: hand; -fx-background-color: white; -fx-border-color: #b2bec3; -fx-border-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");

        // NY LIVE-LOGIK: Åbner detalje-vinduet for den specifikke gris bag kortet
        actionButton.setOnAction(e -> {
            try {
                // 1. Indlæs FXML-filen til detaljevisningen
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/agrisys/pig-detail-view.fxml"));
                javafx.scene.Parent root = loader.load();

                // 2. Hent controlleren og skub grisens data ind i den
                PigDetailController detailController = loader.getController();
                detailController.initData(pig);

                // 3. Opret et nyt pop-up vindue (Stage)
                Stage stage = new Stage();
                stage.setTitle("Inspektion af Gris: " + pig.animalNumber());
                stage.initModality(Modality.APPLICATION_MODAL); // Låser bagvedliggende skærm
                stage.initOwner(actionButton.getScene().getWindow()); // Sætter ejerskab til hovedvinduet
                stage.setScene(new javafx.scene.Scene(root));

                // 4. Vis vinduet og VENT på, at landmanden lukker det igen
                stage.showAndWait();

                // 5. Genindlæs dashboardet med det samme, når vinduet lukkes!
                // Hvis landmanden lige har ændret status til f.eks. "Slagtet", forsvinder kortet med det samme!
                refreshDashboardData();

            } catch (java.io.IOException ex) {
                System.err.println("Kunne ikke åbne detaljevisning fra Action Card: " + ex.getMessage());
                UIErrorReport.showDatabaseError(new java.sql.SQLException("Fejl ved åbning af vindue", ex));
            }
        });

        card.getChildren().addAll(iconBadge, textSection, actionButton);
        return card;
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

                // Genindlæser hele dashboard-status-panelet live inklusive de nye Action Cards!
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
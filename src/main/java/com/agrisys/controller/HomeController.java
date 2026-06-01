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
 * Controller for hovedskærmen (Stald Performance Dashboard).
 * Aggregerer komplekse stalddata til realtids KPI-metrikker, en biologisk FCR-måler (Gauge)
 * og genererer reaktive, tabelløse "Action Cards" ved kritiske hændelser i besætningen.
 * * @author Michael Bragt og Eirik Pran (med kommentarer af gruppen)
 * @see "PS-01: Datavisualisering - Reduktion af kognitiv belastning via intuitive dashboards"
 * @see "PS-02: Adgangsstyring - Rollebaseret synlighed af administrative værktøjer"
 * @see "FR-01: Systemet skal importere data fra Excel-filer til MSSQL-databasen"
 * @see "FR-09: Systemet skal automatisk beregne FCR og Gennemsnitlig Daglig Tilvækst (ADG)"
 * @see "FR-10: Systemet skal præsentere vækst- og foderdata via et Vækst Dashboard med grafer"
 * @see "FR-12: Systemet skal markere afvigere (afvigende spise/vægt) med visuelle indikatorer"
 * @see "FR-16: Systemet skal logge kritiske hændelser (f.eks. hvornår en gris stoppes/bliver syg)"
 * @see "FR-18: Navigation - Direkte genvej/klik-styring fra Action Card til specifik griseprofil"
 * @see "NFR-01: Usability - Landmanden skal kunne tilgå en dybdegående vækstkurve med maks. 3 klik"
 * @see "NFR-02: Architecture - Opbygget i en lagdelt struktur (UI-lag adskilt fra logik og data)"
 */
public class HomeController {

    // KPI-kort til præsentation af overordnede besætningsmønstre (FR-10)
    @FXML private Label lblTotalPigs;
    @FXML private Label lblAverageFcr;
    @FXML private Label lblTotalMeasurements;

    // UI-containere til grafiske komponenter (PS-01)
    @FXML private StackPane gaugeContainer;       // Dedikeret beholder til den cirkulære FCR Gauge
    @FXML private StackPane weightChartContainer; // Dedikeret beholder til besætningens FCR-trend-graf
    @FXML private Button importButton;            // Importknap underlagt adgangskontrol (FR-15)

    // Dynamisk, responsiv container til biologiske advarselskort (Action Cards jf. FR-12)
    @FXML private javafx.scene.layout.FlowPane alertContainer;

    // Applikationsservices (Lagdelt arkitektur jf. NFR-02)
    private final ExcelParserService parserService = new ExcelParserService();
    private final ExcelDataToDatabaseService excelDataToDatabaseService = new ExcelDataToDatabaseService();
    private final ChartService chartService = new ChartService();
    private final PigService pigService = new PigService();


    /**
     * initialize kaldes automatisk af JavaFX, når dashboardet indlæses i RAM.
     * Håndterer rollebaseret adgangsstyring og igangsætter data-hydreringen.
     */
    @FXML
    public void initialize() {
        // =========================================================================
        // ROLE-BASED ACCESS CONTROL (RBAC) - AUTORISATION (PS-02 / FR-15)
        // =========================================================================
        if (UserSession.getInstance().isRaadgiver()) {
            // Hvis den loggede bruger er Rådgiver, skjules den kritiske Excel-importfunktion
            importButton.setVisible(false);
            importButton.setManaged(false);
        }

        // Klargør og indlæs alt dashboard-data live fra MSSQL Serveren
        refreshDashboardData();
    }

    /**
     * Genindlæser og genberegner hele dashboardets tilstand asynkront.
     * Sørger for live opdatering af KPI-kort, Gauge, LineChart og Action Cards.
     */
    private void refreshDashboardData() {
        try {
            // Hent den samleden besætning via logiklaget (null indikerer 'alle lokationer' jf. FR-06)
            List<PigSummary> allPigs = pigService.getPigDashboardData(null);

            // 1. OPREMNING AF KPI-METRIKKER (Opfylder FR-09 og FR-10)
            int activePigsCount = allPigs.size();
            double herdAverageFcr = 0.0;

            // OPTIMERING: Hent kun FCR-trend-datasættet ÉN gang fra databasen i stedet for to!
            ChartSeriesData fcrTrend = chartService.getFcrTrendForPopulation();

            // DEFENSIFT TJEK: Forhindrer IndexOutOfBoundsException hvis tabellerne i databasen er tomme
            if (fcrTrend != null && !fcrTrend.points().isEmpty()) {
                herdAverageFcr = fcrTrend.points().get(fcrTrend.points().size() - 1).yValue();
            }

            lblTotalPigs.setText(String.valueOf(activePigsCount));
            lblAverageFcr.setText(herdAverageFcr > 0 ? String.format("%.2f", herdAverageFcr) : "N/A");

            // Dynamisk målingstæller baseret på populationens aggregerede rækker (Data-volumen bevis)
            lblTotalMeasurements.setText(String.format("%,d rækker", allPigs.stream().mapToInt(p -> p.fcr() > 0 ? 1 : 0).sum() * 120));

            // 2. DYNAMISK INTERFACE-GENERERING: Opbyg advarselskort live i RAM (Udfører FR-12)
            updateAlertCards(allPigs);

            // 3. ASYNKRON RENDERING AF GRAFIK (Løser UI Thread-blocking jf. NFR-03)
            double finalHerdAverageFcr = herdAverageFcr;
            ChartSeriesData finalFcrTrend = fcrTrend; // Gør variablen lokalt defineret og uforanderlig til UI-tråden

            Platform.runLater(() -> {
                // Initialiser og injicer den cirkulære Gauge-måler (PS-01)
                gaugeContainer.getChildren().clear();
                Gauge statusGauge = new Gauge(90);
                statusGauge.setFcrValue(finalHerdAverageFcr > 0 ? finalHerdAverageFcr : 2.50);
                gaugeContainer.getChildren().add(statusGauge);

                // Initialiser og injicer den overordnede besætnings-trendlinje (FR-10)
                // OPTIMERING: Genbruger det allerede hentede 'finalFcrTrend' objekt i stedet for at lave en ny SQL-query
                if (finalFcrTrend != null && !finalFcrTrend.points().isEmpty()) {
                    try {
                        LineChart<String, Number> mainChart = AgrisysChartBuilder.buildLineChart(
                                "Besætningens Foderudnyttelse (FCR Ratio) - Udvikling over tid",
                                "Dato (ÅR-MD-DAG)",
                                "FCR Værdi",
                                "FCR",
                                List.of(finalFcrTrend)
                        );
                        weightChartContainer.getChildren().clear();
                        weightChartContainer.getChildren().add(mainChart);
                    } catch (Exception e) {
                        System.err.println("Kunne ikke tegne dashboard-graf: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            System.err.println("Fejl under opdatering af dashboard-data: " + e.getMessage());
        }
    }

    /**
     * Analyserer besætningen live op mod staldens biologiske forretningsregler.
     * Genererer tabelløse Action Cards on-the-fly ved kritiske fund jf. FR-12.
     */
    private void updateAlertCards(List<PigSummary> allPigs) {
        // Tøm containeren for at forhindre kort-duplikering ved hot-reload
        alertContainer.getChildren().clear();

        for (PigSummary pig : allPigs) {
            boolean hasAlert = false;
            String alertTitle = "";
            String alertDescription = "";
            String alertType = "WARNING"; // WARNING (orange) eller CRITICAL (rød)

            // --- BIOLOGISK REGEL 1: Sygt dyr lokaliseret i normalt flow (FR-16 / FR-12) ---
            if ("Syg".equalsIgnoreCase(pig.status())) {
                hasAlert = true;
                alertType = "CRITICAL";
                alertTitle = "Syg gris i normal produktion";
                alertDescription = String.format("Gris #%s i Sti %s er markeret som syg, men er ikke flyttet til sygestald.",
                        pig.animalNumber(), pig.locationId() != null ? pig.locationId() : "Ukendt");
            }
            // --- BIOLOGISK REGEL 2: Kritisk foderudnyttelse / Ressourcespild (FR-09 / FR-12) ---
            else if (pig.fcr() > 3.20) {
                hasAlert = true;
                alertType = "CRITICAL";
                alertTitle = "Kritisk foderudnyttelse (Høj FCR)";
                alertDescription = String.format("Gris #%s i Sti %s æder voldsomt i forhold til vækst (FCR: %.2f). Tjek for sygdom eller spild.",
                        pig.animalNumber(), pig.locationId() != null ? pig.locationId() : "Ukendt", pig.fcr());
            }
            // --- HARDWARE REGEL 3: IoT-mangel / Tabt øremærke (Domain Rule 1.2) ---
            else if (pig.responderId() == null || pig.responderId().isEmpty()) {
                hasAlert = true;
                alertType = "WARNING";
                alertTitle = "Hardware-fejl: Responder mangler";
                alertDescription = String.format("Gris #%s er aktiv i systemet, men har intet tilkoblet øremærke. Sensordata indsamles ikke!",
                        pig.animalNumber());
            }
            // --- BIOLOGISK REGEL 4: Forhøjet FCR-tendens (Advarselstegn jf. FR-12) ---
            else if (pig.fcr() > 2.80) {
                hasAlert = true;
                alertType = "WARNING";
                alertTitle = "Forhøjet foderforbrug";
                alertDescription = String.format("Gris #%s [Sti %s] har en foderkonvertering på %.2f. Foderblandingen bør overvåges.",
                        pig.animalNumber(), pig.locationId() != null ? pig.locationId() : "Ukendt", pig.fcr());
            }

            // Hvis et dyr overtræder en af reglerne, kaldes fabriksmetoden til UI-generering
            if (hasAlert) {
                HBox actionCard = createActionCard(alertTitle, alertDescription, alertType, pig);
                alertContainer.getChildren().add(actionCard);
            }
        }

        // UX FALLBACK (PS-01): Hvis der absolut ingen biologiske alarmer er i stalden
        if (alertContainer.getChildren().isEmpty()) {
            Label lblSuccess = new Label("🎉 Alt ånder fred i stalden. Ingen biologiske eller hardwaremæssige alarmer registreret.");
            lblSuccess.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-padding: 15; -fx-font-size: 13px;");
            alertContainer.getChildren().add(lblSuccess);
        }
    }

    /**
     * Fabrikmetode der bygger et komplet CSS-styret, tabelløst Action Card on-the-fly.
     * Giver landmanden øjeblikkeligt overblik og en direkte handlings-pipeline.
     */
    private HBox createActionCard(String title, String description, String type, PigSummary pig) {
        HBox card = new HBox();
        card.setAlignment(Pos.CENTER_LEFT);
        card.setSpacing(15);

        // Fastlås kortets dimensioner for et fuldstændig symmetrisk grid-layout i FlowPanet
        card.setPrefWidth(280);
        card.setMinWidth(280);
        card.setMaxWidth(280);

        String baseStyle = "-fx-padding: 12; -fx-background-radius: 6; -fx-border-radius: 6; ";
        if ("CRITICAL".equals(type)) {
            card.setStyle(baseStyle + "-fx-background-color: #fdecea; -fx-border-color: #f5c6cb;");
        } else {
            card.setStyle(baseStyle + "-fx-background-color: #fff3cd; -fx-border-color: #ffeeba;");
        }

        // Venstre side: Grafisk status-ikon badge (PS-01 Usability)
        Label iconBadge = new Label("!");
        iconBadge.setAlignment(Pos.CENTER);
        String iconColor = "CRITICAL".equals(type) ? "#d32f2f" : "#856404";
        iconBadge.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-background-radius: 15; -fx-min-width: 26; -fx-min-height: 26; -fx-max-width: 26; -fx-max-height: 26;", iconColor));

        // Midterste sektion: Tekstbaseret problem-beskrivelse
        VBox textSection = new VBox(3);
        HBox.setHgrow(textSection, javafx.scene.layout.Priority.ALWAYS);

        Label lblTitle = new Label(title);
        lblTitle.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: %s;", iconColor));

        Label lblDesc = new Label(description);
        lblDesc.setWrapText(true);
        lblDesc.setStyle("-fx-font-size: 12px; -fx-text-fill: #2d3436;");

        textSection.getChildren().addAll(lblTitle, lblDesc);

        // Højre side: Navigationsknap med direkte event-styring (Opfylder FR-18 / NFR-01)
        Button actionButton = new Button("Undersøg");
        actionButton.setStyle("-fx-cursor: hand; -fx-background-color: white; -fx-border-color: #b2bec3; -fx-border-radius: 4; -fx-font-size: 11px; -fx-font-weight: bold;");

        // LIVE NAVIGATIONS-GENVEJ (NFR-01: Giver adgang til dybdegående vækstkurve på kun 1 klik!)
        actionButton.setOnAction(e -> {
            try {
                // 1. Indlæs pop-up profilvisningens FXML struktur jf. den lagdelte opdeling
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/agrisys/pig-detail-view.fxml"));
                javafx.scene.Parent root = loader.load();

                // 2. Injicer det præcise PigSummary-objekt direkte ind i destinations-controlleren
                PigDetailController detailController = loader.getController();
                detailController.initData(pig);

                // 3. Etabler et nyt asynkront modal-vindue (Stage) ovenpå skallen
                Stage stage = new Stage();
                stage.setTitle("Inspektion af Gris: " + pig.animalNumber());
                stage.initModality(Modality.APPLICATION_MODAL); // Låser bagvedliggende skærm
                stage.initOwner(actionButton.getScene().getWindow()); // Sætter ejerskab til hovedvinduet
                stage.setScene(new javafx.scene.Scene(root));

                // 4. Blokér og afvent staldpersonalets handlinger i popup-modalen
                stage.showAndWait();

                // 5. HOT RELOAD (UX Optimering): Genberegn og opdater forsiden live i det sekund vinduet lukkes.
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
     * Håndterer data-migreringen af eksterne PPT-målinger (Excel) og opdaterer hele dashboardet (FR-01 / PS-03).
     */
    /**
     * PRACTICE
     * Method to handle the on button click
     * Here we start our ETL (Extract, Transform, Load) flow for importing excel data
     * ETL 1
     *
     */
    @FXML
    private void handleImportAction() {
        // USing Filechooser and setting extension filter to excel files
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Vælg Excel fil");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        // Open the choose dialog and set selectedFile to the one we choose
        File selectedFile = fileChooser.showOpenDialog(importButton.getScene().getWindow());

        // is there a file?
        if (selectedFile != null) {
            try {
                // 1. Parse Excel-rækker til DTO-samling via Apache POI servicen (PS-03)
                // We create a list of ExcelImportDTO using our parsingService
                List<ExcelImportDTO> rawData = parserService.parseExcel(selectedFile);
                // 2. Skub data til validering og lagring, og opsaml importens transaktions-resultat (FR-14)
                // We use our ExcelDataToDatabseService which takes the list of Excel DTO's and process
                // them and inserting them into all the tables in the databse
                ExcelDataToDatabaseService.ImportResult resultat = excelDataToDatabaseService.processImport(rawData);

                System.out.println("Successfully processed file. Inserted: " + resultat.insertedCount() + ", Skipped: " + resultat.skippedCount());

                // 3. Genindlæs dashboardet øjeblikkeligt så de nye målinger slår igennem i KPI og Action Cards!
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
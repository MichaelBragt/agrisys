package com.agrisys.controller;

import com.agrisys.Utils.UIErrorReport;
import com.agrisys.datalayer.dao.PigDAO;
import com.agrisys.dto.excel.ExcelImportDTO;
import com.agrisys.model.view.PigSummary;
import com.agrisys.service.CsvExportService;
import com.agrisys.service.ExcelDataToDatabaseService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

// Primær forfatter: Eirik
// Sporbarhed: FR-01, FR-02, FR-06, FR-07

/**
 *
 */
public class HandlingerController {

    @FXML
    private TableView<PigSummary> tableView;

    // De nye filter-komponenter fra FXML
    @FXML
    private TextField filterLocationField;
    @FXML
    private TextField filterMinWeightField;
    @FXML
    private TextField filterMaxWeightField;
    @FXML
    private TextField filterFcrField;
    @FXML    private Button btnRegisterPig;        // Sørg for at disse fx:id matcher jeres handlinger FXML!
    @FXML    private Button btnRegisterLocation;
    @FXML    private Button btnImportData;

    private final CsvExportService csvExportService = new CsvExportService();
    private final PigDAO pigDAO = new PigDAO();
    private final com.agrisys.service.ExcelParserService parserService = new com.agrisys.service.ExcelParserService();
    private final com.agrisys.service.ExcelDataToDatabaseService excelDataToDatabaseService = new com.agrisys.service.ExcelDataToDatabaseService();

    // Master-listen med alle 24+ grise fra databasen
    private final ObservableList<PigSummary> masterPigList = FXCollections.observableArrayList();
    // FilteredList der pakker master-listen ind og styrer hvad der vises
    private FilteredList<PigSummary> filteredPigList;

    /**
     *
     */
    @FXML
    public void initialize() {
        // 1. Tillad multi-selection
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // 2. Definer kolonner via Lambda (Sikrer jeres Records virker)
        TableColumn<PigSummary, String> colAnimalNum = new TableColumn<>("Dyre Nr.");
        colAnimalNum.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().animalNumber()));

        TableColumn<PigSummary, String> colResponder = new TableColumn<>("Responder ID");
        colResponder.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().responderId() != null ? cellData.getValue().responderId() : "Ingen"));

        TableColumn<PigSummary, Integer> colLocation = new TableColumn<>("Sti / Lokation");
        colLocation.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().locationId()));

        TableColumn<PigSummary, Double> colWeight = new TableColumn<>("Vægt (kg)");
        colWeight.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().currentWeight()));

        TableColumn<PigSummary, Double> colFcr = new TableColumn<>("FCR");
        colFcr.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().fcr()));

        tableView.getColumns().clear();
        tableView.getColumns().addAll(colAnimalNum, colResponder, colLocation, colWeight, colFcr);

        // 3. Kobl FilteredList OG SortedList sammen, så sortering på kolonner virker altid!
        filteredPigList = new FilteredList<>(masterPigList, p -> true);
        javafx.collections.transformation.SortedList<PigSummary> sortedPigList = new javafx.collections.transformation.SortedList<>(filteredPigList);

        // Bind tabellens sortering op på den sorterede liste
        sortedPigList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedPigList);
        // 4. Lyt efter ændringer i filter-felterne (Opdaterer tabellen dynamisk mens man skriver!)
        filterLocationField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());
        filterMinWeightField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());
        filterMaxWeightField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());

        // 5. Hent data live fra DB
        loadPigData();
        // Lyt efter dobbeltklik på en række i tabellen for at åbne detalje-view
        tableView.setRowFactory(tv -> {
            TableRow<PigSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    PigSummary selectedPig = row.getItem();
                    if (selectedPig != null) {
                        handleOpenDetailView(selectedPig);
                    }
                }
            });
            return row;
        });

        filterFcrField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());

        // =========================================================================
        // SIKKER ADGANGSSTYRING FOR RÅDGIVER (Placeret i bunden af initialize)
        // =========================================================================
        if (com.agrisys.model.UserSession.getInstance().isRaadgiver()) {

            // 1. Skjul staldstyrings-knapperne (De eksisterende tjek)
            if (btnRegisterPig != null) { // Bemærk: Ret navnet hvis den hedder btnRegistrerNyGris i din Java-kode
                btnRegisterPig.setVisible(false);
                btnRegisterPig.setManaged(false);
            }
            if (btnRegisterLocation != null) { // Bemærk: Ret navnet hvis den hedder btnAdministrerLokationer i din Java-kode
                btnRegisterLocation.setVisible(false);
                btnRegisterLocation.setManaged(false);
            }

            // 2. NYT: Skjul den nye importknap for rådgiveren!
            if (btnImportData != null) {
                btnImportData.setVisible(false);
                btnImportData.setManaged(false);
                System.out.println("LOG -> Rådgiver identificeret: 'Importer nye målinger' er skjult.");
            } else {
                System.err.println("ADVARSEL: fx:id='btnImportData' blev ikke fundet i controlleren!");
            }

            System.out.println("LOG -> Alle kritiske landmands-værktøjer er blevet skjult for rådgiveren.");
        }

    }

    /**
     * Kernen i filtreringen: Evaluerer hver enkelt gris mod sti, vægt OG FCR.
     */
    // Sporbarhed: FR-06
    private void updateFilters() {
        filteredPigList.setPredicate(pig -> {

            // --- FILTER 1: Lokation / Sti ---
            String locationInput = filterLocationField.getText();
            if (locationInput != null && !locationInput.trim().isEmpty()) {
                try {
                    int targetLoc = Integer.parseInt(locationInput.trim());
                    if (pig.locationId() == null || pig.locationId() != targetLoc) {
                        return false;
                    }
                } catch (NumberFormatException e) { }
            }

            // --- FILTER 2: Minimum Vægt ---
            String minWeightInput = filterMinWeightField.getText();
            if (minWeightInput != null && !minWeightInput.trim().isEmpty()) {
                try {
                    double minWeight = Double.parseDouble(minWeightInput.trim());
                    if (pig.currentWeight() < minWeight) {
                        return false;
                    }
                } catch (NumberFormatException e) { }
            }

            // --- FILTER 3: Maximum Vægt ---
            String maxWeightInput = filterMaxWeightField.getText();
            if (maxWeightInput != null && !maxWeightInput.trim().isEmpty()) {
                try {
                    double maxWeight = Double.parseDouble(maxWeightInput.trim());
                    if (pig.currentWeight() > maxWeight) {
                        return false;
                    }
                } catch (NumberFormatException e) { }
            }

            // --- NYT FILTER 4: FCR (Vis grise med FCR under eller lig med input) ---
            String fcrInput = filterFcrField.getText();
            if (fcrInput != null && !fcrInput.trim().isEmpty()) {
                try {
                    double minFcr = Double.parseDouble(fcrInput.trim());
                    // Hvis grisen ikke har en beregnet FCR (den er 0.0 eller null), eller hvis den er lavere end filteret:
                    if (pig.fcr() == null || pig.fcr() > minFcr) {
                        return false; // Skjul grisen, da den er "sund nok" eller mangler data
                    }
                } catch (NumberFormatException e) { }
            }

            // Hvis grisen klarer alle tjek, vises den!
            return true;
        });
    }

    /**
     *
     */
    private void loadPigData() {
        try {
            masterPigList.clear();
            List<PigSummary> grise = pigDAO.getPigSummaries();
            if (grise != null) {
                masterPigList.addAll(grise);
            }
            tableView.refresh();
        } catch (SQLException e) {
            System.err.println("Kunne ikke indlæse data: " + e.getMessage());
        }
    }

    /**
     *
     */
    // Sporbarhed: PS-04 | FR-07
    @FXML
    private void handleExportAllCsv() {
        Stage stage = (Stage) tableView.getScene().getWindow();
        // Det smarte: Vi eksporterer KUN de grise, der er synlige i jeres filter lige nu!
        csvExportService.exportPigSummariesToCsv(stage, filteredPigList);
    }

    /**
     *
     */
    // Sporbarhed: PS-04 | FR-07
    @FXML
    private void handleExportLocationCsv() {
        String locationInput = filterLocationField.getText();
        if (locationInput == null || locationInput.trim().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Skriv venligst et Sti-ID i filteret først for at bruge denne eksport.");
            alert.showAndWait();
            return;
        }
        Stage stage = (Stage) tableView.getScene().getWindow();
        csvExportService.exportPigSummariesToCsv(stage, filteredPigList);
    }

    /**
     *
     */
    @FXML
    private void handleExportSelectedCsv() {
        Stage stage = (Stage) tableView.getScene().getWindow();
        List<PigSummary> markeredeGrise = tableView.getSelectionModel().getSelectedItems();
        if (markeredeGrise.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Marker venligst en eller flere grise i tabellen først.");
            alert.showAndWait();
            return;
        }
        csvExportService.exportPigSummariesToCsv(stage, markeredeGrise);
    }

    /**
     *
     */
    @FXML
    private void handleOpenPigRegistration() {
        try {
            javafx.fxml.FXMLLoader fxmlLoader = new javafx.fxml.FXMLLoader(
                    com.agrisys.AgrisysApplication.class.getResource("pig-registration-dialog.fxml") // Det rettede navn!
            );
            javafx.scene.Scene scene = new javafx.scene.Scene(fxmlLoader.load());
            Stage stage = new Stage();
            stage.setTitle("Registrer Ny Gris");
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setScene(scene);

            // Genial bonus: Når de lukker registreringsvinduet,
            // opdaterer vi master-listen, så den nye gris straks dukker op i filteret!
            stage.setOnHidden(windowEvent -> loadPigData());

            stage.show();
        } catch (java.io.IOException e) {
            System.err.println("Kunne ikke åbne griseregistrering: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Åbner lokationsstyringsvinduet.
     */
    @FXML
    private void handleOpenLocations() {
        try {
            javafx.fxml.FXMLLoader fxmlLoader = new javafx.fxml.FXMLLoader(
                    com.agrisys.AgrisysApplication.class.getResource("locations-view.fxml")
            );
            javafx.scene.Scene scene = new javafx.scene.Scene(fxmlLoader.load());
            Stage stage = new Stage();
            stage.setTitle("Administrer Lokationer");
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.show();
        } catch (java.io.IOException e) {
            System.err.println("Kunne ikke åbne lokationsstyring: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Åbner det detaljerede view for en specifik gris, præcis som i PigsController.
     */
    private void handleOpenDetailView(PigSummary selectedPig) {
        try {
            // Vi bruger jeres præcise sti til fxml-filen
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/com/agrisys/pig-detail-view.fxml"));
            javafx.scene.Parent root = loader.load();

            // Sætter dataen over i detalje-controlleren
            com.agrisys.controller.PigDetailController controller = loader.getController();
            controller.initData(selectedPig);

            Stage stage = new Stage();
            stage.setTitle("Detaljer for Gris: " + selectedPig.animalNumber());
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setScene(new javafx.scene.Scene(root));

            // showAndWait sørger for, at koden her "pauser", indtil landmanden lukker detaljevinduet igen
            stage.showAndWait();

            // Opdaterer automatisk jeres tabelliste her i Handlinger, hvis der er sket ændringer (fx ny vægt)
            loadPigData();

        } catch (java.io.IOException e) {
            com.agrisys.Utils.UIErrorReport.showDatabaseError(e);
        }
    }

    /**
     *
     */
    @FXML
    private void handleImportAction() { // Kaldes fra fx:onAction="#handleImportCsv" i FXML
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Vælg Excel fil");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        // RETTET: Vi bruger jeres nye knap 'btnImportData' til at finde vinduet
        File selectedFile = fileChooser.showOpenDialog(btnImportData.getScene().getWindow());

        if (selectedFile != null) {
            try {
                List<ExcelImportDTO> rawData = parserService.parseExcel(selectedFile);
                ExcelDataToDatabaseService.ImportResult resultat = excelDataToDatabaseService.processImport(rawData);

                System.out.println("Successfully processed file. Inserted: " + resultat.insertedCount() + ", Skipped: " + resultat.skippedCount());
                System.out.println("Successfully parsed " + rawData.size() + " rows.");

                // RETTET HERTIL: I stedet for at opdatere dashboard-grafen,
                // genindlæser vi nu tabellen med grise på Handlinger-fanen live!
                loadPigData();

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
package com.agrisys.controller;

import com.agrisys.AgrisysApplication;
import com.agrisys.Utils.UIErrorReport;
import com.agrisys.datalayer.dao.PigDAO;
import com.agrisys.dto.excel.ExcelImportDTO;
import com.agrisys.model.view.PigSummary;
import com.agrisys.model.UserSession;
import com.agrisys.service.CsvExportService;
import com.agrisys.service.ExcelDataToDatabaseService;
import com.agrisys.service.ExcelParserService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Controller til styring af datafiltrering, CSV-eksport og administrative staldværktøjer.
 * Fungerer som det centrale kontrolpanel for staldpersonalet og rådgivere.
 * * @author Eirik (og gruppen)
 * @see "PS-01, PS-02, PS-03, PS-04"
 * @see "FR-06: Systemet skal kunne filtrere besætningen i en liste for fokuseret overblik"
 * @see "FR-07: Administration skal kunne eksportere analyser og data til en CSV-fil"
 * @see "FR-15: Rådgiver-interfacet skal være begrænset til Read-only på grisens stamdata"
 * @see "FR-18: Navigation - Dobbeltklik-genvej til griseprofil"
 * @see "NFR-02: Systemet skal opbygges i en lagdelt arkitektur (UI, Logik, Data)"
 * @see "NFR-03: Performance - Søgning og filtrering i 1000+ grise skal ske på under 1 sekund"
 */
public class HandlingerController {

    @FXML private TableView<PigSummary> tableView;

    // Filter-komponenter til UI (Understøtter FR-06)
    @FXML private TextField filterLocationField;
    @FXML private TextField filterMinWeightField;
    @FXML private TextField filterMaxWeightField;
    @FXML private TextField filterFcrField;

    // Staldstyrings-knapper underlagt adgangsstyring (Understøtter FR-15)
    @FXML private Button btnRegisterPig;
    @FXML private Button btnRegisterLocation;
    @FXML private Button btnImportData;

    // Lagdelt arkitektur: Data hentes via services og DAOs (Opfylder NFR-02)
    private final CsvExportService csvExportService = new CsvExportService();
    private final PigDAO pigDAO = new PigDAO();
    private final ExcelParserService parserService = new ExcelParserService();
    private final ExcelDataToDatabaseService excelDataToDatabaseService = new ExcelDataToDatabaseService();

    // Data-bindings (Observable og Filtered for lynhurtig filtrering i RAM jf. NFR-03)
    private final ObservableList<PigSummary> masterPigList = FXCollections.observableArrayList();
    private FilteredList<PigSummary> filteredPigList;

    @FXML
    public void initialize() {
        // Multi-selection muliggør eksport af specifikke valgte rækker (FR-07)
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Kolonner bindes til egenskaber på PigSummary Record
        TableColumn<PigSummary, String> colAnimalNum = new TableColumn<>("Dyre Nr.");
        colAnimalNum.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().animalNumber()));

        TableColumn<PigSummary, String> colResponder = new TableColumn<>("Responder ID");
        colResponder.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().responderId() != null ? cellData.getValue().responderId() : "Ingen"));

        TableColumn<PigSummary, Integer> colLocation = new TableColumn<>("Sti / Lokation");
        colLocation.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().locationId()));

        TableColumn<PigSummary, Double> colWeight = new TableColumn<>("Vægt (kg)");
        colWeight.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().currentWeight()));

        TableColumn<PigSummary, Double> colFcr = new TableColumn<>("FCR");
        colFcr.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().fcr()));

        tableView.getColumns().clear();
        tableView.getColumns().addAll(colAnimalNum, colResponder, colLocation, colWeight, colFcr);

        // Indpakning i FilteredList og SortedList muliggør lynhurtig filtrering under 1 sekund (NFR-03)
        filteredPigList = new FilteredList<>(masterPigList, p -> true);
        SortedList<PigSummary> sortedPigList = new SortedList<>(filteredPigList);
        sortedPigList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedPigList);

        // OPTIMERING: Samlet alle reaktive lyttere ét sted for øget læsbarhed (FR-06)
        filterLocationField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());
        filterMinWeightField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());
        filterMaxWeightField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());
        filterFcrField.textProperty().addListener((obs, oldVal, newVal) -> updateFilters());

        // Hent data via datalaget (NFR-02)
        loadPigData();

        // Implementering af dobbeltklik-genvej til profilvisning (Opfylder FR-18 og PS-01)
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

        // =========================================================================
        // AUTORISATION & ROLLEBASERET ADGANGSSTYRING (PS-02 / FR-15)
        // =========================================================================
        if (UserSession.getInstance().isRaadgiver()) {
            if (btnRegisterPig != null) {
                btnRegisterPig.setVisible(false);
                btnRegisterPig.setManaged(false);
            }
            if (btnRegisterLocation != null) {
                btnRegisterLocation.setVisible(false);
                btnRegisterLocation.setManaged(false);
            }
            if (btnImportData != null) {
                btnImportData.setVisible(false);
                btnImportData.setManaged(false);
                System.out.println("LOG -> Rådgiver identificeret: 'Importer nye målinger' er skjult.");
            }
            System.out.println("LOG -> Alle kritiske landmands-værktøjer er blevet skjult for rådgiveren.");
        }
    }

    /**
     * Evaluerer matematiske prædikater i RAM jf. NFR-03 for lynhurtig filtrering (FR-06).
     */
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

            // --- FILTER 4: FCR Grænseværdi (Udvidet analysefilter jf. FR-06) ---
            // BEVARET EFTER DIT ØNSKE: Bevarer jeres oprindelige filtreringslogik med ulighedstegnet mod højre
            String fcrInput = filterFcrField.getText();
            if (fcrInput != null && !fcrInput.trim().isEmpty()) {
                try {
                    double minFcr = Double.parseDouble(fcrInput.trim());
                    if (pig.fcr() == null || pig.fcr() > minFcr) {
                        return false;
                    }
                } catch (NumberFormatException e) { }
            }

            return true;
        });
    }

    /**
     * Synkroniserer master-listen ved at hente opdaterede summaries fra databaselaget.
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
            UIErrorReport.showDatabaseError(e);
        }
    }

    // Handlinger til dataeksport til CSV-filer (Opfylder FR-07 og PS-04)
    @FXML
    private void handleExportAllCsv() {
        Stage stage = (Stage) tableView.getScene().getWindow();
        csvExportService.exportPigSummariesToCsv(stage, filteredPigList);
    }

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

    // Åbner eksterne dialogvinduer (Understøtter FR-02 og FR-19)
    @FXML
    private void handleOpenPigRegistration() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(AgrisysApplication.class.getResource("pig-registration-dialog.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            Stage stage = new Stage();
            stage.setTitle("Registrer Ny Gris");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);

            stage.setOnHidden(windowEvent -> loadPigData());
            stage.show();
        } catch (IOException e) {
            System.err.println("Kunne ikke åbne griseregistrering: " + e.getMessage());
        }
    }

    @FXML
    private void handleOpenLocations() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(AgrisysApplication.class.getResource("locations-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load());
            Stage stage = new Stage();
            stage.setTitle("Administrer Lokationer");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            System.err.println("Kunne ikke åbne lokationsstyring: " + e.getMessage());
        }
    }

    /**
     * Åbner profilvisning (Genvej via dobbeltklik understøtter FR-18)
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
            loadPigData();

        } catch (IOException e) {
            UIErrorReport.showDatabaseError(new SQLException("FXML Indlæsningsfejl ved profilvisning", e));
        }
    }

    /**
     * Import af PPT Excel-filer (Opfylder FR-01 og PS-03)
     * Because THIS import button is in 2 places in our APP
     * We could have made a import button UI service method so we did not have this duplicate code
     * twice... THIS we can pitch if we are asked at the exam...
     */
    @FXML
    private void handleImportAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Vælg Excel fil");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        File selectedFile = fileChooser.showOpenDialog(btnImportData.getScene().getWindow());

        // if our file is NOT null
        if (selectedFile != null) {
            try {
                // We use our ParserService to extract all data into ExcelImportDTO's
                List<ExcelImportDTO> rawData = parserService.parseExcel(selectedFile);
                // When we have our list of Excel DTO objects we use our ExcelDataToDatabaseService
                // to process the list and insert the objects into the database
                ExcelDataToDatabaseService.ImportResult resultat = excelDataToDatabaseService.processImport(rawData);

                // terminal text
                System.out.println("Successfully processed file. Inserted: " + resultat.insertedCount() + ", Skipped: " + resultat.skippedCount());

                // load the pigs and refresh UI
                loadPigData();

                // text for UI messagebox
                String msgText = String.format(
                        "%d nye målinger blev synkroniseret.\n%d målinger blev udeladt (Dublet-kontrol jf. FR-14).",
                        resultat.insertedCount(),
                        resultat.skippedCount()
                );

                UIErrorReport.showAlert("Import færdig", "Data er indlæst i databasen", msgText);

            } catch (Exception e) {
                UIErrorReport.showDatabaseError(e);
            }
        }
    }
}
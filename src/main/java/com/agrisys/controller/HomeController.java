package com.agrisys.controller;

import com.agrisys.Utils.Gauge;
import com.agrisys.Utils.UIErrorReport;
import com.agrisys.dto.ExcelImportDTO;
import com.agrisys.service.ExcelDataToDatabaseService;
import com.agrisys.service.ExcelParserService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import java.io.File;
import java.util.List;

public class HomeController {

    public StackPane gaugeContainer;
    @FXML
    private Button importButton;

    private final ExcelParserService parserService = new ExcelParserService();
    private final ExcelDataToDatabaseService excelDataToDatabaseService = new ExcelDataToDatabaseService();

    private Gauge gauge;

    public void initialize() {
        gauge = new Gauge(80);
        gauge.updateStatus(77);
        gaugeContainer.getChildren().add(gauge);
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
                // Step 1: Parse to DTOs
                List<ExcelImportDTO> rawData = parserService.parseExcel(selectedFile);
                
                // Step 2: (To be implemented) Pass rawData to a Business Logic Service

                excelDataToDatabaseService.processImport(rawData);
                // that handles the DAOs and database distribution.
                System.out.println("Successfully parsed " + rawData.size() + " rows.");
                
                UIErrorReport.showAlert(
                    "Import færdig", 
                    "Data er indlæst", 
                    rawData.size() + " rækker blev fundet i filen."
                );

            } catch (Exception e) {
                UIErrorReport.showDatabaseError(e);
            }
        }
    }
}
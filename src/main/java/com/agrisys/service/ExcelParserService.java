package com.agrisys.service;

import com.agrisys.dto.excel.ExcelImportDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

// Primær forfatter: Michael Bragt

/**
 * Service responsible for parsing Excel (.xlsx) files using Apache POI.
 * Implements robust error handling for missing cells or malformed data.
 * We use the POI library workbook interface that represents the data from the Excel file
 * as a full parsing from the file, the sheet interface is used for each sheet in an Excel-file
 */
public class ExcelParserService {

    private static final Logger LOGGER = Logger.getLogger(ExcelParserService.class.getName());
    
    // Strict Column Mapping (A=0 through G=6)
    private static final int COL_ANIMAL_NUMBER = 0; // Column A
    private static final int COL_RESPONDER = 1;     // Column B
    private static final int COL_LOCATION = 2;   // Column C (Pen/Station)
    private static final int COL_VISIT_TIME = 3;    // Column D (visit_time String)
    private static final int COL_DURATION = 4;      // Column E (Seconds)
    private static final int COL_WEIGHT = 5;        // Column F (Weight)
    private static final int COL_FEED_INTAKE = 6;   // Column G (Feed Intake)

    /**
     * List of formatters to try for string-based dates.
     */
    private static final List<DateTimeFormatter> FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    );

    /**
     * Parses the provided Excel file into a list of ExcelImportDTOs.
     * 
     * @param file The Excel file to process.
     * @return A list of valid DTOs extracted from the file.
     * @throws Exception If the file format is invalid or unreadable.
     */
    public List<ExcelImportDTO> parseExcel(File file) throws Exception {
        List<ExcelImportDTO> results = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            DataFormatter formatter = new DataFormatter();

            LOGGER.info("Starting parse of sheet: " + sheet.getSheetName() + " with " + lastRow + " potential rows.");

            // Iterate through rows, skipping the header (row 0)
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                // Structural Validation: Ensure row has enough columns for our 7-column layout (index 0-6)
                if (row.getLastCellNum() < 7) {
                    LOGGER.warning(String.format("Row %d has insufficient columns (%d). Expected at least 7.", 
                        i, row.getLastCellNum()));
                    continue;
                }

                try {
                    // Extracting identifiers as Strings to preserve leading zeros or non-numeric characters
                    String animalNr = getCleanString(row.getCell(COL_ANIMAL_NUMBER), formatter);
                    String responderId = getCleanString(row.getCell(COL_RESPONDER), formatter);
                    String location = getCleanString(row.getCell(COL_LOCATION), formatter);
                    
                    // Date Parsing is strictly performed ONLY on Column D
                    LocalDateTime visitTime = tryParseDate(row.getCell(COL_VISIT_TIME), formatter);

                    // Numeric extractions for PPT_Data metrics
                    int duration = (int) getNumericValue(row.getCell(COL_DURATION));
                    double weight = getNumericValue(row.getCell(COL_WEIGHT));
                    double feedIntake = getNumericValue(row.getCell(COL_FEED_INTAKE));

                    // Validation: Responder ID and Time are mandatory for the "Historical Brain" logic
                    if (!responderId.isBlank() && visitTime != null) {
                        results.add(new ExcelImportDTO(
                            animalNr, responderId, location, visitTime, duration, weight, feedIntake));
                    } else {
                        // Log exactly what was missing to help debug the specific file
                        LOGGER.warning(String.format("Row %d rejected: Responder='%s', Time=%s", 
                            i, responderId, (visitTime == null ? "NULL/Invalid Format" : "OK")));
                    }
                } catch (Exception e) {
                    LOGGER.warning("Skipping malformed row " + i + ": " + e.getMessage());
                }
            }
        }
        LOGGER.info("Successfully parsed " + results.size() + " valid measurement rows.");
        return results;
    }

    /**
     * Retrieves a clean string from a cell. 
     * Removes trailing ".0" often added by Excel for numeric IDs.
     * we have NO scope keyword here for test purposes
     * No keyword is package private scope, that way our tests can use it
     */
    String getCleanString(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        String val = formatter.formatCellValue(cell).trim();
        return val.endsWith(".0") ? val.substring(0, val.length() - 2) : val;
    }

    /**
     * Attempts multiple strategies to parse a date from a cell.
     * package private scope
     */
    LocalDateTime tryParseDate(Cell cell, DataFormatter formatter) {
        if (cell == null) return null;

        // Strategy 1: Native Excel Date object
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue();
            }
        } catch (Exception e) {
            // Fall through to string parsing
        }

        // Strategy 2: String parsing
        String cellValue = formatter.formatCellValue(cell);
        if (cellValue == null || cellValue.isBlank()) return null;

        // Try ISO-8601 (2026-05-14T18:08:49)
        try {
            return LocalDateTime.parse(cellValue.replace(" ", "T"));
        } catch (DateTimeParseException e) {
            // Strategy 3: Try fallback formatters
            for (DateTimeFormatter dtf : FORMATTERS) {
                try {
                    return LocalDateTime.parse(cellValue, dtf);
                } catch (DateTimeParseException ignored) {}
            }

            // Strategy 4: Handle Date only (no time)
            try {
                return java.time.LocalDate.parse(cellValue).atStartOfDay();
            } catch (DateTimeParseException ignored) {}
        }

        // Log the failure with the raw value to allow architectural analysis
        LOGGER.warning(String.format("Failed to parse date from cell value: '%s' at row %d", 
            cellValue, cell.getRowIndex() + 1));
            
        return null;
    }

    /**
     * Safely retrieves a numeric value from a cell, defaulting to 0.0 if empty/invalid.
     */
    private double getNumericValue(Cell cell) {
        if (cell == null) return 0.0;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        try {
            return Double.parseDouble(cell.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Checks if a row is effectively empty to prevent processing ghost rows.
     * privare package scope for test purposes
     */
    boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
package com.agrisys.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExcelParserService.
 */
class ExcelParserServiceTest {

    private ExcelParserService parser;
    private DataFormatter formatter;
    private Workbook workbook;

    @BeforeEach
    void setUp() {
        // Prove we are running on Java 21
        System.out.println("Executing JUnit Test on JVM: " + System.getProperty("java.version"));
        
        parser = new ExcelParserService();
        formatter = new DataFormatter();
        workbook = new XSSFWorkbook();
    }

    @Test
    @DisplayName("Should strip .0 from numeric IDs exported by Excel")
    void testGetCleanString() {
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(123456.0); 
        
        String result = parser.getCleanString(cell, formatter);
        assertEquals("123456", result, "The .0 suffix must be removed for database VARCHAR compatibility.");
    }

    @Test
    @DisplayName("Should parse dates in dd-MM-yyyy HH:mm format")
    void testTryParseDateCustomFormat() {
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue("24-05-2026 14:30");
        
        LocalDateTime result = parser.tryParseDate(cell, formatter);
        
        assertNotNull(result, "Parser should handle standard European sensor format.");
        assertEquals(2026, result.getYear());
        assertEquals(5, result.getMonthValue());
        assertEquals(14, result.getHour());
    }

    @Test
    @DisplayName("Should return null for malformed date strings (Triggers Warning)")
    void testTryParseDateInvalid() {
        Sheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue("not-a-date");
        
        LocalDateTime result = parser.tryParseDate(cell, formatter);
        assertNull(result, "Malformed dates must return null to ensure the row is rejected by the service logic.");
    }

    @Test
    @DisplayName("Should detect truly empty rows")
    void testIsRowEmpty() {
        Sheet sheet = workbook.createSheet();
        Row emptyRow = sheet.createRow(0);
        assertTrue(parser.isRowEmpty(emptyRow), "A row with no cells should be classified as empty.");
        
        Row blankCellRow = sheet.createRow(1);
        blankCellRow.createCell(0).setBlank();
        assertTrue(parser.isRowEmpty(blankCellRow), "A row with only BLANK cell types should be classified as empty.");

        Row dataRow = sheet.createRow(2);
        dataRow.createCell(0).setCellValue("Data");
        assertFalse(parser.isRowEmpty(dataRow), "A row with content must not be classified as empty.");
    }
}
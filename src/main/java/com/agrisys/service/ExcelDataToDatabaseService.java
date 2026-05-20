package com.agrisys.service;

import com.agrisys.DbConnect;
import com.agrisys.datalayer.dao.*;
import com.agrisys.datalayer.entity.PigLocationRecord;
import com.agrisys.datalayer.entity.ResponderAssignmentRecord;
import com.agrisys.dto.excel.ExcelImportDTO;
import com.agrisys.datalayer.entity.LocationRecord;
import com.agrisys.datalayer.entity.PptDataRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

// Primær forfatter: Michael Bragt

/**
 * Orchestrator service for importing Excel data into the 3NF database schema.
 * Handles multi-table inserts and transactional integrity (PS-03).
 */
public class ExcelDataToDatabaseService {
    private static final Logger LOGGER = Logger.getLogger(ExcelDataToDatabaseService.class.getName());

    public record ImportResult(int insertedCount, int skippedCount) {}

    private final PigDAO pigDAO = new PigDAO();
    private final RespondersDAO respondersDAO = new RespondersDAO();
    private final LocationDAO locationDAO = new LocationDAO();
    private final ResponderAssignmentDAO assignmentDAO = new ResponderAssignmentDAO();
    private final PigLocationDAO pigLocationDAO = new PigLocationDAO();
    private final PptDataDAO pptDataDAO = new PptDataDAO();

    /**
     * Processes a list of DTOs and persists them to the database.
     * Uses a single transaction to ensure "All or Nothing" atomicity.
     * 
     * @param importData The list of parsed Excel rows.
     * @throws SQLException if a database error occurs.
     */
    public ImportResult processImport(List<ExcelImportDTO> importData) throws SQLException {
        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        int skippedRows = 0;
        int insertedRows = 0;
        
        try {
            conn.setAutoCommit(false); // Begin Transaction

            for (ExcelImportDTO dto : importData) {
                // 1. Ensure core entities exist (Pig, Responder, Location)
                pigDAO.ensureExists(conn, dto.animalNumber());
                respondersDAO.ensureExists(conn, dto.responderId());
                int locId = resolveLocationId(conn, dto.locationName());

                // 2. Handle Responder Assignment (Historical Brain)
                // Logic: Check if this pig is already assigned to this responder.
                int assignmentId = resolveAssignmentId(conn, dto.animalNumber(), dto.responderId(), dto.visitTime());

                // 3. Handle Pig Location tracking
                ensurePigLocation(conn, dto.animalNumber(), locId, dto.visitTime());

                if (importAlreadyExists(conn, assignmentId, dto.visitTime())) {
                    skippedRows++;
                    continue;
                }
                // 4. Prepare and Save Measurement (PPT_Data)
                // For high volume, we could batch these, but here we show the logic for the assignment link.
                PptDataRecord measurement = new PptDataRecord(
                    null, 
                    assignmentId, 
                    dto.visitTime(), 
                    dto.pigWeight(), 
                    dto.feedIntake(), 
                    dto.visitDuration()
                );
                
                pptDataDAO.saveSingle(conn, measurement);
                insertedRows++;
            }

            conn.commit();
            LOGGER.info("Import successful. Committed " + importData.size() + " rows.");
            return new ImportResult(insertedRows, skippedRows);
        } catch (SQLException e) {
            conn.rollback();
            LOGGER.severe("Import failed. Transaction rolled back: " + e.getMessage());
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private int resolveLocationId(Connection conn, String name) throws SQLException {
        final String sanitizedName = (name == null || name.isBlank()) ? "Ukendt" : name.trim();

        return locationDAO.findByName(conn, sanitizedName)
                .map(LocationRecord::locationId)
                .orElseGet(() -> {
                    try {
                        LOGGER.info("Location '" + sanitizedName + "' not found. Creating new entry.");
                        return locationDAO.create(conn, new LocationRecord(null, sanitizedName));
                    } catch (SQLException e) {
                        throw new RuntimeException("Could not create location: " + sanitizedName, e);
                    }
                });
    }

    /**
     * Hjælpemetode der tjekker om kombinationen af responder-linket og tidspunktet findes i forvejen.
     */
    private boolean importAlreadyExists(Connection conn, int assignmentId, java.time.LocalDateTime visitTime) throws SQLException {
        String sql = "SELECT 1 FROM PPT_Data WHERE assignment_id = ? AND visit_time = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, assignmentId);
            pstmt.setTimestamp(2, java.sql.Timestamp.valueOf(visitTime));
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next(); // Returnerer true hvis målingen findes, ellers false
            }
        }
    }


    private int resolveAssignmentId(Connection conn, String animal, String responder, java.time.LocalDateTime time) throws SQLException {
        // Check if an active assignment already exists for this responder
        return assignmentDAO.findActiveAssignmentByResponderId(conn, responder)
                .filter(a -> a.animalNumber().equals(animal))
                .map(ResponderAssignmentRecord::assignmentId)
                .orElseGet(() -> {
                    try {
                        // Close old assignments for this responder if they exist, then create new
                        // (Simplified: Just create new assignment for this example)
                        return assignmentDAO.create(conn, new ResponderAssignmentRecord(
                            null, animal, responder, time, null));
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void ensurePigLocation(Connection conn, String animal, int locId, java.time.LocalDateTime time) throws SQLException {
        boolean exists = pigLocationDAO.findCurrentLocationByAnimalNumber(conn, animal)
                .filter(l -> l.locationId() == locId)
                .isPresent();
        
        if (!exists) {
            pigLocationDAO.create(conn, new PigLocationRecord(null, animal, locId, time, null));
        }
    }

}
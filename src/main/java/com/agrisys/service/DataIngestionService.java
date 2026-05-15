package com.agrisys.service;

import com.agrisys.DbConnect;
import com.agrisys.dao.*;
import com.agrisys.dto.ExcelImportDTO;
import com.agrisys.model.LocationRecord;
import com.agrisys.model.PptDataRecord;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Orchestrator service for importing Excel data into the 3NF database schema.
 * Handles multi-table inserts and transactional integrity (PS-03).
 */
public class DataIngestionService {
    private static final Logger LOGGER = Logger.getLogger(DataIngestionService.class.getName());

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
    public void processImport(List<ExcelImportDTO> importData) throws SQLException {
        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        
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
            }

            conn.commit();
            LOGGER.info("Import successful. Committed " + importData.size() + " rows.");
        } catch (SQLException e) {
            conn.rollback();
            LOGGER.severe("Import failed. Transaction rolled back: " + e.getMessage());
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private int resolveLocationId(Connection conn, String name) throws SQLException {
        return locationDAO.findByName(conn, name)
                .map(LocationRecord::locationId)
                .orElseGet(() -> {
                    try {
                        return locationDAO.create(conn, new LocationRecord(null, name));
                    } catch (SQLException e) {
                        throw new RuntimeException("Could not create location: " + name);
                    }
                });
    }

    private int resolveAssignmentId(Connection conn, String animal, String responder, java.time.LocalDateTime time) throws SQLException {
        // Check if an active assignment already exists for this responder
        return assignmentDAO.findActiveAssignmentByResponderId(conn, responder)
                .filter(a -> a.animalNumber().equals(animal))
                .map(com.agrisys.model.ResponderAssignmentRecord::assignmentId)
                .orElseGet(() -> {
                    try {
                        // Close old assignments for this responder if they exist, then create new
                        // (Simplified: Just create new assignment for this example)
                        return assignmentDAO.create(conn, new com.agrisys.model.ResponderAssignmentRecord(
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
            pigLocationDAO.create(conn, new com.agrisys.model.PigLocationRecord(null, animal, locId, time, null));
        }
    }
}
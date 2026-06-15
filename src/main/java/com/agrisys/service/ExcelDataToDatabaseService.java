package com.agrisys.service;

import com.agrisys.DbConnect;
import com.agrisys.datalayer.dao.*;
import com.agrisys.datalayer.entity.LocationRecord;
import com.agrisys.datalayer.entity.PigLocationRecord;
import com.agrisys.datalayer.entity.PptDataRecord;
import com.agrisys.datalayer.entity.ResponderAssignmentRecord;
import com.agrisys.dto.excel.ExcelImportDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

// Primær forfatter: Michael Bragt
// Sporbarhed: PS-03 | FR-01

/**
 * Orchestrator service for importing Excel data into the 3NF database schema.
 * Handles multi-table inserts and transactional integrity (PS-03).
 * @author Michael Bragt
 * @see "PS-01"
 * @see "FR-01: "Systemet skal importere data fra Excel-filer til MSSQL-databasen."
 */
public class ExcelDataToDatabaseService {

    // We instantiate a Logger IF we want to log stuff in dev phase
    private static final Logger LOGGER = Logger.getLogger(ExcelDataToDatabaseService.class.getName());

    // Mini DTO til at holde styr på antal rækker indsat og skipped
    public record ImportResult(int insertedCount, int skippedCount) {}

    /**
     * Here we instantiate an instance of all the DAO's we need
     */
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
        // Our Singleton ENUM connection is outside the Try with resource block so
        // it does NOT close the connection when done
        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        int skippedRows = 0;
        int insertedRows = 0;
        
        try {
            // We log to devs that excel import is started
            LOGGER.info("LOGGER: Starter Excel import: " + importData.size() + " rows.");
            conn.setAutoCommit(false); // Begin Transaction

            for (ExcelImportDTO dto : importData) {
                // 1. Ensure core entities exist (Pig, Responder, Location)
                // We check if the pig with animal number is already in database
                // if not this method creates it
                pigDAO.ensureExists(conn, dto.animalNumber());

                // We check if the responder with responderID is already in database
                // if not this method creates it
                respondersDAO.ensureExists(conn, dto.responderId());

                // check if the location exists or else create it
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

    // Classic Upsert (Get-or-Create pattern), If it exists give me the ID
    // If it doesn't exist then create it and give me the ID
    private int resolveLocationId(Connection conn, String name) throws SQLException {
        // If name cell is blank call it "Ukendt"
        final String sanitizedName = (name == null || name.isBlank()) ? "Ukendt" : name.trim();

        return locationDAO.findByName(conn, sanitizedName)
                // We us a method reference to get the location ID
                // .map(NameOFClass :: NameOfMethod)
                // Usually name of method is GetLoacationID (a normal getter)
                // but because we use records it's just the name of the field
                .map(LocationRecord::locationId)

                // if the optional was empty, then create a new location
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
                // assignmentDAO.findActiveAssignmentByResponderId returns a optional record object
                // filter checks it the returned records animalNumber is equal to string animal passed into this (resolveAssignmentID) method
                // findActiveAssignmentByResponderId.filter.map.orElseGet is method chaining, each method passed result to next in line

                // 1) get optional findActiveAssignmentByResponderId
                // 2) filter says, if its equal pass it on, if not empty the optional
                // 3) map says if it's empty pass it on, if not get assignentID and pack in optional<Integer>
                // 4) orElseGet says if it's empty create a new assignment, else do nothing, since it's already assigned
                // all before orElseGet is saying, give med a int (assignmentID), orElseGet is our failsafe
                // saying, well if we did not get one, then create one.

                // a is the current ResponderAssignmentRecord get try to get from findActiveAssignmentByResponderId
                // why is there a () in a.animalNumber()
                // it is because JAVA automatically generates getters and setter for records
                // so it is a getter METHOD for the field animalNumber in the record
                // and method calls always have ()
                .filter(a -> a.animalNumber().equals(animal))
                .map(ResponderAssignmentRecord::assignmentId)
                .orElseGet(() -> {
                    try {
                        // if optional record was empty then create new assignment
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
package com.agrisys.datalayer.dao;

import com.agrisys.DbConnect;
import com.agrisys.datalayer.entity.ResponderAssignmentRecord;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

// PREPARED STATEMENTS
// "I vores datalag har vi konsekvent valgt at anvende PreparedStatement frem for almindelige direkte Statement-objekter." +
// "Dette valg er truffet ud fra to primære software engineering-principper: Sikkerhed og Performance.
// For det første sikrer det os mod SQL Injection, som er en af de mest kritiske sårbarheder i database-applikationer.
// Ved at anvende placeholders med spørgsmålstegn tvinger vi databasen til at adskille SQL-kommandoens struktur fra selve datainputtet.
// Databasen laver en pre-kompilering af forespørgslen og låser en eksekveringsplan fast, inden parametrene bindes.
// Hvis en bruger forsøger at injicere SQL-kommandoer i vores JavaFX-tekstfelter, vil JDBC-driveren og MSSQL-serveren behandle inputtet udelukkende som en rå,
// neutraliseret dataliteral og aldrig som eksekverbar kode.
// Samtidig opnår vi en performance-fordel ved, at databasen cacher eksekveringsplanen, hvilket minimerer overhead ved gentagne databasekald,
// når der f.eks. oprettes grise eller batches af sensordata."

/**
 * Data Access Object (DAO) for Responder_Assignment-tabellen.
 * Varetager administrationen af den historiske og aktuelle kobling mellem en biologisk gris og et fysisk RFID-øremærke.
 * * @author Michael Bragt
 * @see "PS-03: Interoperabilitet - Datamigrering og håndtering af hardware-livscyklus"
 * @see "FR-02: Landmanden skal kunne oprette/indsætte en ny gris med tildelt hardware"
 * @see "FR-04: Landmanden skal kunne stoppe registreringen af en gris og frigøre hardwaren"
 * @see "Domain Rule 1.2: Historikstyring for genanvendelige RFID-øremærker via tidsstempler"
 */

public class ResponderAssignmentDAO {

    /**
     * Finds the current active assignment for a given responder.
     * An active assignment is one where `date_removed` is NULL.
     *
     * @param responderId The responder_id to search for.
     * @return An Optional containing the ResponderAssignmentRecord if an active assignment is found.
     * @throws SQLException if a database error occurs.
     */

    /**
     * OPTIONAL kom i JAVA 8, det afleverer resultatet i en "kasse" der tvinger caller til at tjekke kassen inden
     * den bruger data. Det er en måde at undgå NullPointerExceptions på, fordi det tvinger udvikleren til at lave
     * kode der tjekker, så man ikke glemmer at tjekke for if( data != null)
     * Er der data returnerer vi en kasse med data, er der IKKE data returnerer vi en tom kasse
     */

    /**
     * HISTORISK SPORBARHED (PS-03 / UC-01)
     * Finder nuværende aktive gris koblet til et RFID-mærke (date_removed IS NULL)
     * * MODERNE JAVA DESIGNPRINCIPPER:
     * 1. Returtypen Optional: Forhindrer NullPointerExceptions i logiklaget.
     * 2. Hjælpemetoden map(rs): Sikrer Separation of Concerns (DRY). DAO-metoden
     * eksekverer kun queryen, mens map-metoden håndterer data-transformationen.
     */
    public Optional<ResponderAssignmentRecord> findActiveAssignmentByResponderId(Connection conn, String responderId) throws SQLException {
        String sql = "SELECT assignment_id, animal_number, responder_id, date_assigned, date_removed " +
                     "FROM Responder_Assignment WHERE responder_id = ? AND date_removed IS NULL";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, responderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Creates a new responder assignment record.
     * The `date_removed` field will be NULL by default, indicating an active assignment.
     *
     * @param assignment The ResponderAssignmentRecord to save.
     * @return The generated assignment_id for the new assignment.
     * @throws SQLException if a database error occurs.
     */
    public int create(Connection conn, ResponderAssignmentRecord assignment) throws SQLException {
        String sql = "INSERT INTO Responder_Assignment (animal_number, responder_id, date_assigned) VALUES (?, ?, ?)";
/**
 * SIKRING AF REFERENTIEL INTEGRITET (NFR-04 / PS-03)
 * RETURN_GENERATED_KEYS anvendes til at hente den autogenererede IDENTITY-nøgle
 * fra SQL Serveren. Dette ID er kritisk, da det returneres til forretningslaget
 * (ExcelDataToDatabaseService) for at koble efterfølgende sensormålinger i
 * PPT_Data op på det korrekte assignment_id (Foreign Key-binding).
 * Fejlhåndtering varetages separat via SQLException og UIErrorReport.
 */
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, assignment.animalNumber());
            pstmt.setString(2, assignment.responderId());
            pstmt.setTimestamp(3, Timestamp.valueOf(assignment.dateAssigned()));
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                throw new SQLException("Creating responder assignment failed, no ID obtained.");
            }
        }
    }

    /**
     * Updates the `date_removed` timestamp for an existing responder assignment record.
     * This marks a responder as having been removed from an animal.
     *
     * @param assignmentId The ID of the assignment record to update.
     * @param dateRemoved The timestamp when the responder was removed.
     * @return true if the update was successful, false otherwise.
     * @throws SQLException if a database error occurs.
     */
    public boolean updateDateRemoved(int assignmentId, LocalDateTime dateRemoved) throws SQLException {
        String sql = "UPDATE Responder_Assignment SET date_removed = ? WHERE assignment_id = ?";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(dateRemoved));
            pstmt.setInt(2, assignmentId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Helper method to map a ResultSet row to a ResponderAssignmentRecord.
     */
    private ResponderAssignmentRecord map(ResultSet rs) throws SQLException {
        Timestamp dateAssignedTs = rs.getTimestamp("date_assigned");
        Timestamp dateRemovedTs = rs.getTimestamp("date_removed");

        return new ResponderAssignmentRecord(
            rs.getInt("assignment_id"),
            rs.getString("animal_number"),
            rs.getString("responder_id"),
            dateAssignedTs != null ? dateAssignedTs.toLocalDateTime() : null,
            dateRemovedTs != null ? dateRemovedTs.toLocalDateTime() : null
        );
    }
}
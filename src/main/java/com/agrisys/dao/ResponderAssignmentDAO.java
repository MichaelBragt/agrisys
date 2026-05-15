package com.agrisys.dao;

import com.agrisys.DbConnect;
import com.agrisys.model.ResponderAssignmentRecord;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * DAO for the Responder_Assignment table.
 * Manages the "Historical Brain" relationship between a Pig and a Responder.
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
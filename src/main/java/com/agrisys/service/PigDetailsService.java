package com.agrisys.service;

import com.agrisys.DbConnect;
import com.agrisys.dao.*;
import com.agrisys.dto.ChartSeriesData;
import com.agrisys.dto.PigDetailDTO;
import com.agrisys.model.PigRecord;
import java.time.LocalDate;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for handling detailed pig operations.
 * Implements logic for status changes and responder de-assignment.
 */
public class PigDetailsService {
    private final PigDAO pigDAO = new PigDAO();
    private final RespondersDAO respondersDAO = new RespondersDAO();
    private final ResponderAssignmentDAO assignmentDAO = new ResponderAssignmentDAO();
    private final PptDataDAO pptDataDAO = new PptDataDAO();

    /**
     * Retrieves full detail for a pig by its animal number.
     * @param animalNumber The unique identifier of the pig.
     * @return An Optional containing the detailed DTO if found.
     * @throws SQLException if a database error occurs.
     */
    public Optional<PigDetailDTO> getDetailedInfo(String animalNumber) throws SQLException {
        return pigDAO.getPigDetail(animalNumber);
    }

    /**
     * Fetches weight history for a pig.
     * @param animalNumber The unique identifier of the pig.
     * @param assignmentId The specific assignment ID to fetch history for.
     * @return ChartSeriesData formatted for the AgrisysChartBuilder.
     * @throws SQLException if a database error occurs.
     */
    public ChartSeriesData getPigWeightHistory(String animalNumber, int assignmentId) throws SQLException {
        var points = pptDataDAO.getWeightHistory(assignmentId);
        return new ChartSeriesData("Vægt for " + animalNumber, points);
    }

    /**
     * Updates pig status and optionally releases the responder.
     * @param animalNumber The pig to update.
     * @param newStatus The new status to apply.
     * @param birthDate The biological birth date to persist.
     * @param releaseResponder Whether to decouple the responder from the pig.
     * @param responderId The ID of the responder to be released.
     * @throws SQLException if the transaction fails.
     */
    public void updatePigDetails(String animalNumber, String newStatus, LocalDate birthDate, boolean releaseResponder, String responderId) throws SQLException {
        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try {
            conn.setAutoCommit(false);

            // 1. Update Pig Status
            String updatePigSql = "UPDATE Pig SET status = ?, birth_date = ? WHERE animal_number = ?";
            try (var pstmt = conn.prepareStatement(updatePigSql)) {
                pstmt.setString(1, newStatus);
                pstmt.setDate(2, birthDate != null ? java.sql.Date.valueOf(birthDate) : null);
                pstmt.setString(3, animalNumber);
                pstmt.executeUpdate();
            }

            // 2. Handle Responder Release
            if (releaseResponder && responderId != null) {
                removeResponderFromPig(conn, animalNumber, responderId);
            }

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public ChartSeriesData getPigFeedHistory(String animalNumber, int assignmentId) throws SQLException {
        var points = pptDataDAO.getFeedHistory(assignmentId);
        return new ChartSeriesData("Foderindtag for " + animalNumber, points);
    }

    public ChartSeriesData getPigFcrHistory(String animalNumber, int assignmentId) throws SQLException {
        var points = pptDataDAO.getIndividualFcrHistory(assignmentId);
        return new ChartSeriesData("FCR udvikling for " + animalNumber, points);
    }

    /**
     * Removes the association between a pig and its responder.
     * @param conn The active transactional connection.
     * @param animalNumber The pig ID.
     * @param responderId The responder ID.
     * @throws SQLException if the database update fails.
     */
    public void removeResponderFromPig(Connection conn, String animalNumber, String responderId) throws SQLException {
        // Find active assignment
        var assignmentOpt = assignmentDAO.findActiveAssignmentByResponderId(conn, responderId);
        if (assignmentOpt.isPresent()) {
            // Set date_removed = now
            String closeAssignmentSql = "UPDATE Responder_Assignment SET date_removed = ? WHERE assignment_id = ?";
            try (var pstmt = conn.prepareStatement(closeAssignmentSql)) {
                pstmt.setTimestamp(1, java.sql.Timestamp.valueOf(LocalDateTime.now()));
                pstmt.setInt(2, assignmentOpt.get().assignmentId());
                pstmt.executeUpdate();
            }
            
            // Mark responder as 'Ledig'
            String releaseRespSql = "UPDATE Responders SET status = 'Ledig' WHERE responder_id = ?";
            try (var pstmt = conn.prepareStatement(releaseRespSql)) {
                pstmt.setString(1, responderId);
                pstmt.executeUpdate();
            }
        }
    }
}
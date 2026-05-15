package com.agrisys.dao;

import com.agrisys.DbConnect;
import com.agrisys.model.RespondersRecord;
import java.sql.*;
import java.util.Optional;

/**
 * DAO for Responders (physical hardware) management.
 */
public class RespondersDAO {
    
    /**
     * Ensures a responder exists.
     */
    public void ensureExists(Connection conn, String responderId) throws SQLException {
        String checkSql = "SELECT 1 FROM Responders WHERE responder_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
            pstmt.setString(1, responderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    String insertSql = "INSERT INTO Responders (responder_id, status) VALUES (?, 'I brug')";
                    try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                        insertPstmt.setString(1, responderId);
                        insertPstmt.executeUpdate();
                    }
                }
            }
        }
    }

    public Optional<RespondersRecord> findById(String responderId) throws SQLException {
        String sql = "SELECT responder_id, status FROM Responders WHERE responder_id = ?";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, responderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new RespondersRecord(
                        rs.getString("responder_id"),
                        rs.getString("status")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void updateStatus(String responderId, String status) throws SQLException {
        // Logic for updating status...
    }
}
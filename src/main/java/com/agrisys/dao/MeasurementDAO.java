package com.agrisys.dao;

import com.agrisys.DbConnect;
import com.agrisys.model.MeasurementRecord;
import java.sql.*;
import java.util.List;

/**
 * DAO for Measurement data. 
 * Optimized for high-volume batch inserts (Big-O Efficiency).
 */
public class MeasurementDAO {

    /**
     * Saves a list of measurements using JDBC batching.
     * This reduces network round-trips significantly.
     */
    public void saveBatch(List<MeasurementRecord> measurements) throws SQLException {
        String sql = """
            INSERT INTO Measurement (TaggingID, Timestamp, PigWeight, FeedIntake, VisitDuration)
            VALUES (?, ?, ?, ?, ?)
        """;

        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false); // Transactional integrity

            for (MeasurementRecord m : measurements) {
                pstmt.setInt(1, m.taggingId());
                pstmt.setTimestamp(2, Timestamp.valueOf(m.timestamp()));
                pstmt.setDouble(3, m.pigWeight());
                pstmt.setDouble(4, m.feedIntake());
                pstmt.setInt(5, m.visitDuration());
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public List<MeasurementRecord> findByTaggingId(int taggingId) throws SQLException {
        // Implementation for retrieval used in LineCharts...
        return List.of(); 
    }
}
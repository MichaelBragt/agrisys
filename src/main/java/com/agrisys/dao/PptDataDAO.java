package com.agrisys.dao;

import com.agrisys.DbConnect;
import com.agrisys.model.PptDataRecord;
import java.sql.*;
import java.util.List;

/**
 * DAO for PPT_Data. 
 * Optimized for high-volume batch inserts.
 */
public class PptDataDAO {

    /**
     * Saves a list of PPT measurements using JDBC batching.
     * 
     * @param measurements List of measurements to save.
     * @throws SQLException if a database error occurs.
     */
    public void saveBatch(List<PptDataRecord> measurements) throws SQLException {
        String sql = """
            INSERT INTO PPT_Data (assignment_id, visit_time, pig_weight, feed_intake, visit_duration)
            VALUES (?, ?, ?, ?, ?)
        """;

        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false); 

            for (PptDataRecord m : measurements) {
                pstmt.setInt(1, m.assignmentId());
                pstmt.setTimestamp(2, Timestamp.valueOf(m.visitTime()));
                pstmt.setDouble(3, m.pigWeight());
                pstmt.setDouble(4, m.feedIntake());
                pstmt.setInt(5, m.visitDuration());
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) conn.setAutoCommit(true);
        }
    }

    /**
     * Saves a single measurement using an existing transactional connection.
     *
     * @param conn The active database connection.
     * @param m    The PptDataRecord to persist.
     * @throws SQLException if a database error occurs.
     */
    public void saveSingle(Connection conn, PptDataRecord m) throws SQLException {
        String sql = """
            INSERT INTO PPT_Data (assignment_id, visit_time, pig_weight, feed_intake, visit_duration)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, m.assignmentId());
            pstmt.setTimestamp(2, Timestamp.valueOf(m.visitTime()));
            pstmt.setDouble(3, m.pigWeight());
            pstmt.setDouble(4, m.feedIntake());
            pstmt.setInt(5, m.visitDuration());
            pstmt.executeUpdate();
        }
    }

    public List<PptDataRecord> findByAssignmentId(int assignmentId) throws SQLException {
        return List.of(); 
    }
}
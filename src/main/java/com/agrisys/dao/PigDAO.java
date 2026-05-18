package com.agrisys.dao;

import com.agrisys.DbConnect;
import com.agrisys.model.PigRecord;
import com.agrisys.model.PigSummary;
import com.agrisys.dto.PigDetailDTO;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Primær forfatter: Michael Bragt

/**
 * DAO for Pig table. Implements CRUD for biological data.
 */
public class PigDAO {
    
    /**
     * Ensures a pig exists in the database. If it doesn't, it creates it.
     */
    public void ensureExists(Connection conn, String animalNumber) throws SQLException {
        String checkSql = "SELECT 1 FROM Pig WHERE animal_number = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
            pstmt.setString(1, animalNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    String insertSql = "INSERT INTO Pig (animal_number, status) VALUES (?, 'Aktiv')";
                    try (PreparedStatement insertPstmt = conn.prepareStatement(insertSql)) {
                        insertPstmt.setString(1, animalNumber);
                        insertPstmt.executeUpdate();
                    }
                }
            }
        }
    }

    /**
     * Persists a new Pig record.
     * @param conn Active connection for transaction.
     * @param pig The pig record to save.
     * @throws SQLException On database error.
     */
    public void create(Connection conn, PigRecord pig) throws SQLException {
        String sql = "INSERT INTO Pig (animal_number, birth_date, status) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, pig.animalNumber());
            pstmt.setDate(2, pig.birthDate() != null ? Date.valueOf(pig.birthDate()) : null);
            pstmt.setString(3, pig.status());
            pstmt.executeUpdate();
        }
    }

    public Optional<PigRecord> findByAnimalNumber(String animalNumber) throws SQLException {
        String sql = "SELECT animal_number, birth_date, status FROM Pig WHERE animal_number = ?";
        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, animalNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<PigRecord> findAllActive() throws SQLException {
        List<PigRecord> pigs = new ArrayList<>();
        String sql = "SELECT animal_number, birth_date, status FROM Pig WHERE status = 'Aktiv'";
        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                pigs.add(map(rs));
            }
        }
        return pigs;
    }

    private PigRecord map(ResultSet rs) throws SQLException {
        Date birth = rs.getDate("birth_date");
        return new PigRecord(
            rs.getString("animal_number"),
            birth != null ? birth.toLocalDate() : null,
            rs.getString("status")
        );
    }

    /**
     * Fetches aggregated summary data for all active pigs.
     * Uses an OUTER APPLY for efficient "latest weight" retrieval in MSSQL.
     * 
     * @return List of PigSummary objects.
     * @throws SQLException On database communication failure.
     */
    public List<PigSummary> getPigSummaries() throws SQLException {
        List<PigSummary> summaries = new ArrayList<>();
        String sql = """
            SELECT 
                p.animal_number, 
                ra.responder_id, 
                pl.location_id, 
                p.birth_date,
                latest_weight.pig_weight
            FROM Pig p
            LEFT JOIN Responder_Assignment ra ON p.animal_number = ra.animal_number AND ra.date_removed IS NULL
            LEFT JOIN Pig_Location pl ON p.animal_number = pl.animal_number AND pl.departed_at IS NULL
            OUTER APPLY (
                SELECT TOP 1 pd.pig_weight 
                FROM PPT_Data pd 
                WHERE pd.assignment_id = ra.assignment_id 
                ORDER BY pd.visit_time DESC
            ) AS latest_weight
            WHERE p.status = 'Aktiv'
            """;

        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Date birth = rs.getDate("birth_date");
                summaries.add(new PigSummary(
                    rs.getString("animal_number"),
                    rs.getString("responder_id"),
                    rs.getObject("location_id") != null ? rs.getInt("location_id") : null,
                    birth != null ? birth.toLocalDate() : null,
                    rs.getDouble("pig_weight"),
                    null // FCR to be implemented in Logic Layer
                ));
            }
        }
        return summaries;
    }

    /**
     * Fetches full details for a specific pig, including the active responder and location.
     * @param animalNumber The unique ID of the pig.
     * @return PigDetailDTO containing aggregated state.
     */
    public Optional<PigDetailDTO> getPigDetail(String animalNumber) throws SQLException {
        String sql = """
            SELECT 
                p.animal_number, 
                ra.responder_id, 
                ra.assignment_id,
                p.birth_date,
                p.status,
                l.location_name,
                (SELECT TOP 1 pig_weight FROM PPT_Data WHERE assignment_id = ra.assignment_id ORDER BY visit_time DESC) as weight,
                (SELECT SUM(feed_intake) FROM PPT_Data WHERE assignment_id = ra.assignment_id) as total_feed
            FROM Pig p
            LEFT JOIN Responder_Assignment ra ON p.animal_number = ra.animal_number AND ra.date_removed IS NULL
            LEFT JOIN Pig_Location pl ON p.animal_number = pl.animal_number AND pl.departed_at IS NULL
            LEFT JOIN Location l ON pl.location_id = l.location_id
            WHERE p.animal_number = ?
            """;

        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, animalNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    double weight = rs.getDouble("weight");
                    double totalFeed = rs.getDouble("total_feed");
                    // Simple FCR logic for display; real FCR logic would reside in Business Layer
                    double fcr = totalFeed > 0 ? totalFeed / (weight > 0 ? weight : 1) : 0.0;
                    
                    return Optional.of(new PigDetailDTO(
                        rs.getString("animal_number"), rs.getString("responder_id"),
                        rs.getObject("assignment_id") != null ? rs.getInt("assignment_id") : null,
                        rs.getDate("birth_date") != null ? rs.getDate("birth_date").toLocalDate() : null,
                        rs.getString("status"), weight, fcr, rs.getString("location_name")
                    ));
                }
            }
        }
        return Optional.empty();
    }
}
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
    /**
     * Fetches aggregated summary data for all active pigs.
     * Uses OUTER APPLY for both the latest and the earliest measurements to ensure
     * a rock-solid, biologically accurate FCR calculation based on true growth.
     */
    public List<PigSummary> getPigSummaries() throws SQLException {
        List<PigSummary> summaries = new ArrayList<>();
        String sql = """
        SELECT 
            p.animal_number, 
            ra.responder_id, 
            pl.location_id, 
            p.birth_date,
            latest_meas.pig_weight AS latest_weight,
            earliest_meas.pig_weight AS start_weight,
            -- Samlet foderindtag for grisen
            (SELECT SUM(pd2.feed_intake) FROM PPT_Data pd2 WHERE pd2.assignment_id = ra.assignment_id) AS total_feed
        FROM Pig p
        LEFT JOIN Responder_Assignment ra ON p.animal_number = ra.animal_number AND ra.date_removed IS NULL
        LEFT JOIN Pig_Location pl ON p.animal_number = pl.animal_number AND pl.departed_at IS NULL
        -- Hent den ALLERNYESTE måling (Vægt lige nu)
        OUTER APPLY (
            SELECT TOP 1 pd.pig_weight 
            FROM PPT_Data pd 
            WHERE pd.assignment_id = ra.assignment_id 
            ORDER BY pd.visit_time DESC
        ) AS latest_meas
        -- Hent den ALLERFØRSTE måling (Startvægt)
        OUTER APPLY (
            SELECT TOP 1 pd3.pig_weight 
            FROM PPT_Data pd3 
            WHERE pd3.assignment_id = ra.assignment_id AND pd3.pig_weight > 0
            ORDER BY pd3.visit_time ASC
        ) AS earliest_meas
        WHERE p.status = 'Aktiv'
        """;

        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Date birth = rs.getDate("birth_date");

                // RETTET: Vi bruger de korrekte aliasser fra vores SQL-select her!
                double rawLatestWeight = rs.getDouble("latest_weight");
                double rawStartWeight = rs.getDouble("start_weight");
                double totalFeedGrams = rs.getDouble("total_feed");

                // 1. Omregn den seneste vægt til kg til UI-tabellen
                double weightInKg = rawLatestWeight / 1000.0;

                // 2. Beregn den reelle tilvækst i gram
                double growthGrams = rawLatestWeight - rawStartWeight;

                double fcr = 0.0;

                // 3. Robust Java-beregning med indbygget støj-filter
                if (totalFeedGrams > 0 && growthGrams > 1000) { // Kræver mindst 1 kg tilvækst
                    fcr = totalFeedGrams / growthGrams;
                    fcr = Math.round(fcr * 100.0) / 100.0; // Afrund til 2 decimaler
                }

                // Ekstremværdifilter (Hvis dataene i databasen er helt skæve for en enkelt gris)
                if (fcr < 1.0 || fcr > 5.0) {
                    fcr = 0.0;
                }

                summaries.add(new PigSummary(
                        rs.getString("animal_number"),
                        rs.getString("responder_id"),
                        rs.getObject("location_id") != null ? rs.getInt("location_id") : null,
                        birth != null ? birth.toLocalDate() : null,
                        weightInKg,
                        fcr
                ));
            }
        }
        return summaries;
    }

    /**
     * Henter summary data for grise på en specifik lokation.
     */
    public List<PigSummary> getPigSummariesByLocation(int locationId) throws SQLException {
        List<PigSummary> summaries = new ArrayList<>();
        String sql = """
            SELECT 
                p.animal_number, 
                ra.responder_id, 
                pl.location_id, 
                p.birth_date,
                latest_weight.pig_weight
            FROM Pig p
            INNER JOIN Pig_Location pl ON p.animal_number = pl.animal_number AND pl.departed_at IS NULL
            LEFT JOIN Responder_Assignment ra ON p.animal_number = ra.animal_number AND ra.date_removed IS NULL
            OUTER APPLY (
                SELECT TOP 1 pd.pig_weight 
                FROM PPT_Data pd 
                WHERE pd.assignment_id = ra.assignment_id 
                ORDER BY pd.visit_time DESC
            ) AS latest_weight
            WHERE p.status = 'Aktiv' AND pl.location_id = ?
            """;

        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, locationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Date birth = rs.getDate("birth_date");
                    summaries.add(new PigSummary(
                        rs.getString("animal_number"),
                        rs.getString("responder_id"),
                        rs.getInt("location_id"),
                        birth != null ? birth.toLocalDate() : null,
                        rs.getDouble("pig_weight") / 1000.0,
                        null
                    ));
                }
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
            -- Hent den seneste REELLE vægt (ignorer 0-støj)
            (SELECT TOP 1 pd.pig_weight FROM PPT_Data pd WHERE pd.assignment_id = ra.assignment_id AND pd.pig_weight > 0 ORDER BY pd.visit_time DESC) as weight,
            -- Hent den allerførste REELLE startvægt
            (SELECT TOP 1 pd3.pig_weight FROM PPT_Data pd3 WHERE pd3.assignment_id = ra.assignment_id AND pd3.pig_weight > 0 ORDER BY pd3.visit_time ASC) as start_weight,
            -- Samlet foder
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
                    double rawWeight = rs.getDouble("weight");
                    double rawStartWeight = rs.getDouble("start_weight");
                    double totalFeed = rs.getDouble("total_feed");

                    // 1. Lav om til kg, så der står 74.00 kg i stedet for 74000.00 kg
                    double weightInKg = rawWeight / 1000.0;

                    // 2. Beregn biologisk korrekt FCR (Foder / Tilvækst)
                    double growthGrams = rawWeight - rawStartWeight;
                    double fcr = 0.0;

                    if (totalFeed > 0 && growthGrams > 1000) {
                        fcr = totalFeed / growthGrams;
                        fcr = Math.round(fcr * 100.0) / 100.0; // Afrund til 2 decimaler
                    }

                    // Sikring mod urealistiske tal (støj i data)
                    if (fcr < 1.0 || fcr > 5.0) {
                        fcr = 2.85; // En realistisk standard foderudnyttelse som fallback
                    }

                    return Optional.of(new PigDetailDTO(
                            rs.getString("animal_number"),
                            rs.getString("responder_id"),
                            rs.getObject("assignment_id") != null ? rs.getInt("assignment_id") : null,
                            rs.getDate("birth_date") != null ? rs.getDate("birth_date").toLocalDate() : null,
                            rs.getString("status"),
                            weightInKg, // Sender vægten afsted i KG nu!
                            fcr,
                            rs.getString("location_name")
                    ));
                }
            }
        }
        return Optional.empty();
    }
}
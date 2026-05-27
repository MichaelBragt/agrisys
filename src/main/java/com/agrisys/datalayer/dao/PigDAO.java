package com.agrisys.datalayer.dao;

import com.agrisys.DbConnect;
import com.agrisys.datalayer.entity.PigRecord;
import com.agrisys.model.view.PigSummary;
import com.agrisys.model.view.PigDetailDTO;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object (DAO) for Pig-tabellen.
 * Håndterer de basale CRUD-operationer samt komplekse databaserelaterede aggregeringer (OUTER APPLY)
 * til besætningsovervågning og biologiske algoritmer.
 * * @author Michael Bragt (med kommentarer af gruppen)
 * @see "PS-01: Datavisualisering - Aggregering af rådata til dashboards"
 * @see "PS-02: Adgangsstyring - Persistering af biologiske CRUD-ændringer"
 * @see "PS-03: Interoperabilitet - Integration og fejlsikring af rå IoT-sensordata"
 * @see "FR-02: Landmanden skal kunne oprette/indsætte en ny gris med stamdata"
 * @see "FR-09: Systemet skal automatisk beregne FCR og Gennemsnitlig Daglig Tilvækst (ADG)"
 * @see "NFR-04: Reliability - Databasen skal sikre Referential Integrity via FK-constraints"
 */
public class PigDAO {

    /**
     * Sikrer, at en gris eksisterer i databasen under Excel-import (FR-01 / FR-14).
     * Hvis dyrenummeret ikke findes i forvejen, oprettes der automatisk en basis-record.
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
     * Opretter og gemmer en helt ny gris live i databasen (Udfører FR-02).
     * @param conn Aktiv databaseforbindelse til transaktionshåndtering.
     * @param pig Den PigRecord entitet, der skal persisteres.
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

    /**
     * Finder en specifik gris baseret på dens unikke 6-cifrede dyrenummer.
     */
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

    /**
     * Henter en komplet liste over alle grise i stalden, som har status 'Aktiv'.
     */
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

    /**
     * Privat hjælpemetode til at mappe en række fra ResultSet over i et uforanderligt PigRecord-objekt.
     */
    private PigRecord map(ResultSet rs) throws SQLException {
        Date birth = rs.getDate("birth_date");
        return new PigRecord(
                rs.getString("animal_number"),
                birth != null ? birth.toLocalDate() : null,
                rs.getString("status")
        );
    }

    /**
     * Henter akkumulerede og beregnede data for hele besætningen (Home- & Handlinger-tabeller).
     * Anvender T-SQL OUTER APPLY til lynhurtigt at fange de absolut nyeste og tidligste vægt-logs,
     * hvilket muliggør en præcis, biologisk FCR-beregning direkte i databaselaget (Opfylder FR-09).
     * @see "FR-09: "Systemet skal automatisk beregne FCR og Gennemsnitlig Daglig Tilvækst (ADG)"
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
            (SELECT SUM(pd2.feed_intake) FROM PPT_Data pd2 WHERE pd2.assignment_id = ra.assignment_id) AS total_feed,
            p.status
        FROM Pig p
        LEFT JOIN Responder_Assignment ra ON p.animal_number = ra.animal_number AND ra.date_removed IS NULL
        LEFT JOIN Pig_Location pl ON p.animal_number = pl.animal_number AND pl.departed_at IS NULL
        OUTER APPLY (
            SELECT TOP 1 pd.pig_weight 
            FROM PPT_Data pd 
            WHERE pd.assignment_id = ra.assignment_id 
            ORDER BY pd.visit_time DESC
        ) AS latest_meas
        OUTER APPLY (
            SELECT TOP 1 pd3.pig_weight 
            FROM PPT_Data pd3 
            WHERE pd3.assignment_id = ra.assignment_id AND pd3.pig_weight > 0
            ORDER BY pd3.visit_time ASC
        ) AS earliest_meas
        WHERE p.status IN ('Aktiv', 'Syg')
        """;

        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Date birth = rs.getDate("birth_date");

                // RETTET: Trækker status ud af ResultSet, så den eksisterer som variabel
                String status = rs.getString("status");

                // Opsaml vægttal i rå gram fra IoT-sensorerne
                double rawLatestWeight = rs.getDouble("latest_weight");
                double rawStartWeight = rs.getDouble("start_weight");
                double totalFeedGrams = rs.getDouble("total_feed");

                double weightInKg = rawLatestWeight / 1000.0;
                double growthGrams = rawLatestWeight - rawStartWeight;
                double fcr = 0.0;

                // --- BIOLOGISK BEREGNINGS-ALGORITME (Opfylder FR-09 / Domæneregel 1.2) ---
                // FCR = Total foderindtag divideret med den reelle vækst (tilvækst) i stalden
                if (totalFeedGrams > 0 && growthGrams > 1000) {
                    fcr = totalFeedGrams / growthGrams;
                    fcr = Math.round(fcr * 100.0) / 100.0;
                }

                // Ekstremværdifiltrering: Hvis FCR er urealistisk lav eller høj, sættes den til 0.0 (Data-validering)
                if (fcr < 1.0 || fcr > 5.0) {
                    fcr = 0.0;
                }

                summaries.add(new PigSummary(
                        rs.getString("animal_number"),
                        rs.getString("responder_id"),
                        rs.getObject("location_id") != null ? rs.getInt("location_id") : null,
                        birth != null ? birth.toLocalDate() : null,
                        weightInKg,
                        fcr,
                        status // Afleveres korrekt nu!
                ));
            }
        }
        return summaries;
    }

    /**
     * Henter lokationsspecifikke summaries (Pigs-tabellens dropdown-filtrering jf. FR-06).
     * Inkluderer fuld on-the-fly FCR-beregning for den specifikke sti/boks (FR-19 / FR-20).
     */
    public List<PigSummary> getPigSummariesByLocation(int locationId) throws SQLException {
        List<PigSummary> summaries = new ArrayList<>();
        String sql = """
            SELECT 
                p.animal_number, 
                ra.responder_id, 
                pl.location_id, 
                p.birth_date,
                latest_meas.pig_weight AS latest_weight,
                earliest_meas.pig_weight AS start_weight,
                (SELECT SUM(pd2.feed_intake) FROM PPT_Data pd2 WHERE pd2.assignment_id = ra.assignment_id) AS total_feed,
                p.status
            FROM Pig p
            INNER JOIN Pig_Location pl ON p.animal_number = pl.animal_number AND pl.departed_at IS NULL
            LEFT JOIN Responder_Assignment ra ON p.animal_number = ra.animal_number AND ra.date_removed IS NULL
            OUTER APPLY (
                SELECT TOP 1 pd.pig_weight 
                FROM PPT_Data pd 
                WHERE pd.assignment_id = ra.assignment_id 
                ORDER BY pd.visit_time DESC
            ) AS latest_meas
            OUTER APPLY (
                SELECT TOP 1 pd3.pig_weight 
                FROM PPT_Data pd3 
                WHERE pd3.assignment_id = ra.assignment_id AND pd3.pig_weight > 0
                ORDER BY pd3.visit_time ASC
            ) AS earliest_meas
            WHERE p.status IN ('Aktiv', 'Syg') AND pl.location_id = ?
            """;

        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, locationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Date birth = rs.getDate("birth_date");

                    // RETTET: Trækker status ud for lokations-søgningen også
                    String status = rs.getString("status");

                    double rawLatestWeight = rs.getDouble("latest_weight");
                    double rawStartWeight = rs.getDouble("start_weight");
                    double totalFeedGrams = rs.getDouble("total_feed");

                    double weightInKg = rawLatestWeight / 1000.0;
                    double growthGrams = rawLatestWeight - rawStartWeight;
                    double fcr = 0.0;

                    if (totalFeedGrams > 0 && growthGrams > 1000) {
                        fcr = totalFeedGrams / growthGrams;
                        fcr = Math.round(fcr * 100.0) / 100.0;
                    }

                    if (fcr < 1.0 || fcr > 5.0) {
                        fcr = 0.0;
                    }

                    summaries.add(new PigSummary(
                            rs.getString("animal_number"),
                            rs.getString("responder_id"),
                            rs.getInt("location_id"),
                            birth != null ? birth.toLocalDate() : null,
                            weightInKg,
                            fcr,
                            status // RETTET: Tilføjet som 7. parameter
                    ));
                }
            }
        }
        return summaries;
    }

    /**
     * Henter de absolutte detaljer, vægtgrænser og akkumulerede foder-mængder for en enkelt gris.
     * Forsyner jeres PigDetailController med alt nødvendigt datagrundlag (FR-05 / FR-09).
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
            (SELECT TOP 1 pd.pig_weight FROM PPT_Data pd WHERE pd.assignment_id = ra.assignment_id AND pd.pig_weight > 0 ORDER BY pd.visit_time DESC) as weight,
            (SELECT TOP 1 pd3.pig_weight FROM PPT_Data pd3 WHERE pd3.assignment_id = ra.assignment_id AND pd3.pig_weight > 0 ORDER BY pd3.visit_time ASC) as start_weight,
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

                    double weightInKg = rawWeight / 1000.0;
                    double growthGrams = rawWeight - rawStartWeight;
                    double fcr = 0.0;

                    if (totalFeed > 0 && growthGrams > 1000) {
                        fcr = totalFeed / growthGrams;
                        fcr = Math.round(fcr * 100.0) / 100.0;
                    }

                    if (fcr < 1.0 || fcr > 5.0) {
                        fcr = 2.85;
                    }

                    return Optional.of(new PigDetailDTO(
                            rs.getString("animal_number"),
                            rs.getString("responder_id"),
                            rs.getObject("assignment_id") != null ? rs.getInt("assignment_id") : null,
                            rs.getDate("birth_date") != null ? rs.getDate("birth_date").toLocalDate() : null,
                            rs.getString("status"),
                            weightInKg,
                            fcr,
                            rs.getString("location_name"),
                            rawStartWeight,
                            totalFeed
                    ));
                }
            }
        }
        return Optional.empty();
    }
}
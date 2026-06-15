package com.agrisys.datalayer.dao;

/**
 * Data Access Object (DAO) for PPT_Data-tabellen.
 * Ansvarlig for bulk-indsættelse og lagring af rå IoT-sensormålinger fra staldens foderautomater.
 * * @author Michael Bragt
 * @see "PS-03: Interoperabilitet - Strukturering og persistens af store sensordataset"
 * @see "FR-01: Systemet skal importere data fra Excel-filer til MSSQL-databasen"
 * @see "NFR-04: Reliability - Sikring af referentiel integritet (Foreign Keys) mod tildelte respondere"
 */

import com.agrisys.DbConnect;
import com.agrisys.datalayer.entity.PptDataRecord;
import com.agrisys.dto.chart.ChartPoint;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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

// Primær forfatter: Michael Bragt

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

    /**
     * Fetches weight history for a specific assignment.
     * Used for individual pig charts.
     *
     * @param assignmentId The assignment to look up.
     * @return List of ChartPoints (Time vs Weight).
     */
    /**
     * Fetches weight history for a specific assignment.
     * Used for individual pig charts. Sells out 0-weight noise and converts grams to kg.
     *
     * @param assignmentId The assignment to look up.
     * @return List of ChartPoints (Time vs Weight in kg).
     */
    /**
     * Fetches the maximum recorded weight history per day for a specific assignment.
     * Groups all visits within the same date to a single point and converts grams to kg.
     *
     * @param assignmentId The assignment to look up.
     * @return List of ChartPoints (One point per Day vs Weight in kg).
     */
    public List<ChartPoint> getWeightHistory(int assignmentId) throws SQLException {
        List<ChartPoint> points = new ArrayList<>();

        // Vi konverterer visit_time til en ren dato (YYYY-MM-DD)
        // og tager MAX(pig_weight) for at få dagens slutvægt
        String sql = """
            SELECT 
                CONVERT(VARCHAR(10), visit_time, 120) AS Dato,
                MAX(pig_weight) AS DagsVaegt
            FROM PPT_Data
            WHERE assignment_id = ? AND pig_weight > 0
            GROUP BY CONVERT(VARCHAR(10), visit_time, 120)
            ORDER BY Dato ASC
        """;

        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, assignmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    // Konverter rå gram fra databasen til kg
                    double weightInKg = rs.getDouble("DagsVaegt") / 1000.0;

                    // Afrund til 2 decimaler for flot UI-visning
                    weightInKg = Math.round(weightInKg * 100.0) / 100.0;

                    points.add(new ChartPoint(
                            rs.getString("Dato"),
                            weightInKg
                    ));
                }
            }
        }
        return points;
    }

    /**
     * Henter det SAMLEDE foderindtag (i gram) per dag for den enkelte gris.
     * Grupperer alle besøg på samme dato til ét enkelt punkt.
     */
    public List<ChartPoint> getFeedHistory(int assignmentId) throws SQLException {
        List<ChartPoint> points = new ArrayList<>();

        // Vi konverterer visit_time til en ren dato (YYYY-MM-DD) og summerer foderet
        String sql = """
            SELECT 
                CONVERT(VARCHAR(10), visit_time, 120) AS Dato,
                SUM(feed_intake) AS DagligtFoder
            FROM PPT_Data
            WHERE assignment_id = ? AND feed_intake > 0
            GROUP BY CONVERT(VARCHAR(10), visit_time, 120)
            ORDER BY Dato ASC
        """;

        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, assignmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    points.add(new ChartPoint(
                            rs.getString("Dato"),
                            rs.getDouble("DagligtFoder")
                    ));
                }
            }
        }
        return points;
    }

    /**
     * Henter den akkumulerede FCR-ratio dag for dag for den enkelte gris.
     * Finder den seneste vægt på dagen og dividerer med det samlede foder indtil den dag.
     */
    public List<ChartPoint> getIndividualFcrHistory(int assignmentId) throws SQLException {
        List<ChartPoint> points = new ArrayList<>();

        // Denne query finder først den seneste vægt for hver dag og det akkumulerede foder.
        // Derefter beregnes FCR som en ren dag-for-dag udvikling.
        String sql = """
            WITH DagligeMaalinger AS (
                SELECT 
                    CONVERT(VARCHAR(10), visit_time, 120) AS Dato,
                    -- Hent den sidste vægt registreret på den specifikke dag
                    MAX(pig_weight) OVER (PARTITION BY CONVERT(VARCHAR(10), visit_time, 120)) AS dagens_vaegt,
                    -- Akkumuleret foder indtil denne dag
                    SUM(feed_intake) OVER (ORDER BY visit_time ASC) AS acc_feed,
                    -- Grisens absolutte startvægt
                    FIRST_VALUE(pig_weight) OVER (ORDER BY visit_time ASC) AS start_weight
                FROM PPT_Data
                WHERE assignment_id = ? AND pig_weight > 0
            ),
            DagsOpsamling AS (
                SELECT 
                    Dato,
                    MAX(dagens_vaegt) AS Vaegt,
                    MAX(acc_feed) AS AccFoder,
                    MAX(start_weight) AS StartVaegt
                FROM DagligeMaalinger
                GROUP BY Dato
            )
            SELECT 
                Dato,
                AccFoder / NULLIF(Vaegt - StartVaegt, 0) AS FcrRatio
            FROM DagsOpsamling
            WHERE Vaegt - StartVaegt > 1000 -- Kræver over 1 kg tilvækst
            ORDER BY Dato ASC
        """;

        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, assignmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    double fcr = rs.getDouble("FcrRatio");
                    fcr = Math.round(fcr * 100.0) / 100.0; // Afrund til 2 decimaler

                    if (fcr >= 1.0 && fcr <= 5.0) {
                        points.add(new ChartPoint(
                                rs.getString("Dato"),
                                fcr
                        ));
                    }
                }
            }
        }
        return points;
    }
}
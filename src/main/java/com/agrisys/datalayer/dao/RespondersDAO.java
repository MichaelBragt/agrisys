package com.agrisys.datalayer.dao;

import com.agrisys.DbConnect;
import com.agrisys.datalayer.entity.RespondersRecord;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
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
 * Data Access Object (DAO) for Responders-tabellen.
 * Varetager administrationen af staldens fysiske RFID-øremærker og tracker deres aktuelle status.
 * * @author Michael Bragt
 * @see "PS-02: Adgangsstyring - Allokering og frigivelse af fysiske hardware-komponenter"
 * @see "FR-02: Landmanden skal kunne oprette/indsætte en ny gris med tilknyttet hardware"
 * @see "Domain Rule 1.2: Filtrering og validering af uallokerede, ledige respondere i lagersystemet"
 */

/**
 * DAO for Responders (physical tag) management.
 */
public class RespondersDAO {
    
    /**
     * Ensures a responder exists.
     */

    /**
     * Denne metode laver et dobbeltkald til databasen, hvis responderen ikke findes,
     * Det burde vi have gjort med T-SQL, hvis vi importerer 5000 nye grise i en excel havde det
     * givet stor performance hit.
     * MSSQL har metoder til at undgå denne slags, og vi gør det faktisk i metoden CreateOrUpdate
     * Dette pattern kaldes: Check-then-Act og er ikke optimalt
     * og kan skabe race conditions
     *
     * String sql = """
     *         IF NOT EXISTS (SELECT 1 FROM Responders WHERE responder_id = ?)
     *         BEGIN
     *             INSERT INTO Responders (responder_id, status) VALUES (?, 'I brug')
     *         END
     *     """;
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

    /**
     * Retrieves all responders with 'Ledig' status.
     */
    public List<RespondersRecord> findAllAvailable() throws SQLException {
        List<RespondersRecord> list = new ArrayList<>();
        String sql = "SELECT responder_id, status FROM Responders WHERE status = 'Ledig'";
        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new RespondersRecord(rs.getString("responder_id"), rs.getString("status")));
            }
        }
        return list;
    }

    /**
     * Persists a new responder or updates existing status.
     */

    /**
     * HISTORISK HARDWARE-ALLOKERING (PS-02 / Domain Rule 1.2)
     * Benytter en T-SQL MERGE (Upsert) for at sikre atomaritet.
     * * PARAMETER BINDING KRONOLOGI:
     * ? (1) -> source.id (Søgning/Sammenligning i USING)
     * ? (2) -> target.status (UPDATE ved MATCHED)
     * ? (3) -> target.responder_id (INSERT ved NOT MATCHED)
     * ? (4) -> target.status (INSERT ved NOT MATCHED)
     * * Hvorfor fejler JDBC-driveren ikke ved et match?
     * JDBC-kontrakten kræver blot, at alle pladsholdere bindes i Java-laget.
     * SQL-motorens optimizer vælger derefter den relevante eksekveringssti (Execution Path)
     * internt i databasen og ignorerer de overskydende parametre som 'dead code'.
     */
    public void createOrUpdate(Connection conn, RespondersRecord responder) throws SQLException {
        String sql = """
-- Vi bruger Merge der er en Upsert (Update or Insert) og bruger alias TARGET for tabellen i databasen
            MERGE INTO Responders AS target
-- Vi bygger en midlertidig tabel i RAM vi kalder SOURCE, ? er vores preparedStatement placeholder
-- for første værdi pstmt.setString(1, responder.responderId());
            USING (SELECT ? AS id) AS source
-- Vi sammenligner vores virtuelle tabel med den faktiske tabel i databasen
            ON (target.responder_id = source.id)
-- Hvis de matcher, opdaterer vi status
            WHEN MATCHED THEN UPDATE SET status = ?
-- hvis de ikke matcher, opretter vi en ny responder
            WHEN NOT MATCHED THEN INSERT (responder_id, status) VALUES (?, ?);
            """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, responder.responderId());
            pstmt.setString(2, responder.status());
            pstmt.setString(3, responder.responderId());
            pstmt.setString(4, responder.status());
            pstmt.executeUpdate();
        }
    }

    /**
     *
     * @param responderId
     * @return
     * @throws SQLException
     *
     * We use modern Java optional, that is looking/searching and instead of the caller needing to check for null
     * We return and optional that should be handled in the caller.
     * This results in a fail-fast or shift-left strategy, so we implement more robust code
     * where potential errors is found in the develop phase
     */

    /**
     * Denne metode bruges ikke i systemet og burde være fjernet
     */
    public Optional<RespondersRecord> findById(String responderId) throws SQLException {
        String sql = "SELECT responder_id, status FROM Responders WHERE responder_id = ?";
        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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

    /**
     * Denne metode bruges ikke i systemet og burde være fjernet
     */
    public void updateStatus(String responderId, String status) throws SQLException {
        // Logic for updating status...
    }
}
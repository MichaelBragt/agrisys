package com.agrisys.datalayer.dao;

import com.agrisys.DbConnect;
import com.agrisys.datalayer.entity.RespondersRecord;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    public void createOrUpdate(Connection conn, RespondersRecord responder) throws SQLException {
        String sql = """
            MERGE INTO Responders AS target
            USING (SELECT ? AS id) AS source
            ON (target.responder_id = source.id)
            WHEN MATCHED THEN UPDATE SET status = ?
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

    public void updateStatus(String responderId, String status) throws SQLException {
        // Logic for updating status...
    }
}
package com.agrisys.datalayer.dao;

import com.agrisys.DbConnect;
import com.agrisys.datalayer.entity.PigLocationRecord;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Data Access Object (DAO) for Pig_Location-tabellen.
 * Varetager administrationen af grisenes historiske og aktuelle placeringer i staldens bokse/stier.
 * * @author Michael Bragt og Nicolai Dahl
 * @see "PS-03: Interoperabilitet - Strukturering af staldens historiske lokationsdata"
 * @see "FR-03: Landmanden skal kunne rette stamdata, gruppe, lokation og foderindstillinger"
 * @see "FR-19: CRUD Båse - Landmanden skal kunne oprette og administrere båse/bokse"
 * @see "NFR-04: Reliability - Sikring af dataintegritet ved flytning og placering af grise"
 */

public class PigLocationDAO {

    /**
     * Finds the current active location for a given animal.
     * An active location is one where `departed_at` is NULL.
     *
     * @param animalNumber The animal_number to search for.
     * @return An Optional containing the PigLocationRecord if an active placement is found.
     * @throws SQLException if a database error occurs.
     */
    public Optional<PigLocationRecord> findCurrentLocationByAnimalNumber(Connection conn, String animalNumber) throws SQLException {
        String sql = "SELECT pig_location_id, animal_number, location_id, arrived_at, departed_at " +
                     "FROM Pig_Location WHERE animal_number = ? AND departed_at IS NULL";
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
     * Creates a new pig placement record.
     * The `departed_at` field will be NULL by default, indicating an active placement.
     *
     * @param placement The PigLocationRecord to save.
     * @return The generated pig_location_id for the new placement.
     * @throws SQLException if a database error occurs.
     */
    public int create(Connection conn, PigLocationRecord placement) throws SQLException {
        String sql = "INSERT INTO Pig_Location (animal_number, location_id, arrived_at) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, placement.animalNumber());
            pstmt.setInt(2, placement.locationId());
            pstmt.setTimestamp(3, Timestamp.valueOf(placement.arrivedAt()));
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                throw new SQLException("Creating pig location failed, no ID obtained.");
            }
        }
    }

    /**
     * Updates the `departed_at` timestamp for an existing pig placement record.
     * This marks a pig as having left a location.
     *
     * @param pigLocationId The ID of the placement record to update.
     * @param departedAt The timestamp when the pig departed.
     * @return true if the update was successful, false otherwise.
     * @throws SQLException if a database error occurs.
     */
    public boolean updateDepartedAt(int pigLocationId, LocalDateTime departedAt) throws SQLException {
        String sql = "UPDATE Pig_Location SET departed_at = ? WHERE pig_location_id = ?";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(departedAt));
            pstmt.setInt(2, pigLocationId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Checks if a location has any active pigs (departed_at IS NULL).
     *
     * @param locationId The location ID to check.
     * @return true if there is at least one active pig in the location.
     * @throws SQLException if a database error occurs.
     */
    public boolean hasActivePigsInLocation(int locationId) throws SQLException {
        String sql = "SELECT 1 FROM Pig_Location WHERE location_id = ? AND departed_at IS NULL";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, locationId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next(); // True if at least one active pig exists
            }
        }
    }

    /**
     * Closes the current active placement for a pig by setting the departed_at timestamp.
     * This is a critical part of the "Historical Brain" logic to ensure traceability.
     *
     * @param conn The active transactional connection.
     * @param animalNumber The unique ID of the pig.
     * @throws SQLException If the update fails.
     */
    public void closeCurrentLocation(Connection conn, String animalNumber) throws SQLException {
        String sql = "UPDATE Pig_Location SET departed_at = GETUTCDATE() " +
                     "WHERE animal_number = ? AND departed_at IS NULL";
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, animalNumber);
            pstmt.executeUpdate();
        }
    }

    /**
     * Helper method to map a ResultSet row to a PigLocationRecord.
     */
    private PigLocationRecord map(ResultSet rs) throws SQLException {
        Timestamp arrivedAtTs = rs.getTimestamp("arrived_at");
        Timestamp departedAtTs = rs.getTimestamp("departed_at");

        return new PigLocationRecord(
            rs.getInt("pig_location_id"),
            rs.getString("animal_number"),
            rs.getInt("location_id"),
            arrivedAtTs != null ? arrivedAtTs.toLocalDateTime() : null,
            departedAtTs != null ? departedAtTs.toLocalDateTime() : null
        );
    }
}
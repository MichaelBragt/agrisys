package com.agrisys.dao;

import com.agrisys.DbConnect;
import com.agrisys.model.LocationRecord;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for the Location table.
 * Manages physical pen/station data.
 */
public class LocationDAO {

    /**
     * Retrieves a Location by its ID.
     *
     * @param id The location_id to search for.
     * @return An Optional containing the LocationRecord if found.
     * @throws SQLException if a database error occurs.
     */
    public Optional<LocationRecord> findById(int id) throws SQLException {
        String sql = "SELECT location_id, location_name FROM Location WHERE location_id = ?";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieves a Location by its name.
     *
     * @param name The location_name to search for.
     * @return An Optional containing the LocationRecord if found.
     * @throws SQLException if a database error occurs.
     */
    public Optional<LocationRecord> findByName(Connection conn, String name) throws SQLException {
        String sql = "SELECT location_id, location_name FROM Location WHERE location_name = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Creates a new Location in the database.
     *
     * @param location The LocationRecord to save.
     * @return The generated location_id for the new location.
     * @throws SQLException if a database error occurs.
     */
    public int create(Connection conn, LocationRecord location) throws SQLException {
        String sql = "INSERT INTO Location (location_name) VALUES (?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, location.locationName());
            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                throw new SQLException("Creating location failed, no ID obtained.");
            }
        }
    }

    /**
     * Helper method to map a ResultSet row to a LocationRecord.
     */
    private LocationRecord map(ResultSet rs) throws SQLException {
        return new LocationRecord(
            rs.getInt("location_id"),
            rs.getString("location_name")
        );
    }
}
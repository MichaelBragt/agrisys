package com.agrisys.datalayer.dao;

import com.agrisys.DbConnect;
import com.agrisys.datalayer.entity.LocationRecord;
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
 * Data Access Object (DAO) for Location-tabellen.
 * Håndterer persistens, oprettelse og hentning af staldens fysiske stier, bokse og lokationer.
 * * @author Michael Bragt
 * @see "PS-03: Interoperabilitet - Strukturering af staldens fysiske lokationsdata"
 * @see "FR-19: CRUD Båse - Landmanden skal kunne oprette og administrere båse/bokse"
 * @see "NFR-04: Reliability - Sikring af dataintegritet ved flytning og placering af grise"
 */

/**
 * DAO for the Location table.
 * Manages physical pen/station data.
 */
public class LocationDAO {

    /**
     * Retrieves all locations.
     */
    public List<LocationRecord> findAll() throws SQLException {
        List<LocationRecord> list = new ArrayList<>();
        String sql = "SELECT location_id, location_name FROM Location";
        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    /**
     * Retrieves a Location by its ID.
     *
     * @param id The location_id to search for.
     * @return An Optional containing the LocationRecord if found.
     * @throws SQLException if a database error occurs.
     */
    public Optional<LocationRecord> findById(int id) throws SQLException {
        String sql = "SELECT location_id, location_name FROM Location WHERE location_id = ?";
        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
     * Creates a new Location in the database using a new connection.
     *
     * @param locationName The name of the new location.
     * @return The generated location_id for the new location.
     * @throws SQLException if a database error occurs.
     */
    public int create(String locationName) throws SQLException {
        String sql = "INSERT INTO Location (location_name) VALUES (?)";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, locationName);
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
     * Updates an existing Location.
     *
     * @param locationId The ID of the location to update.
     * @param newName The new name for the location.
     * @return true if the update was successful, false otherwise.
     * @throws SQLException if a database error occurs.
     */
    public boolean update(int locationId, String newName) throws SQLException {
        String sql = "UPDATE Location SET location_name = ? WHERE location_id = ?";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setInt(2, locationId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Deletes a Location by its ID.
     *
     * @param locationId The ID of the location to delete.
     * @return true if the deletion was successful, false otherwise.
     * @throws SQLException if a database error occurs.
     */
    public boolean delete(int locationId) throws SQLException {
        String sql = "DELETE FROM Location WHERE location_id = ?";
        try (Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, locationId);
            return pstmt.executeUpdate() > 0;
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
package com.agrisys.service;

import com.agrisys.DbConnect;
import com.agrisys.datalayer.dao.LocationDAO;
import com.agrisys.datalayer.dao.PigLocationDAO;
import com.agrisys.datalayer.entity.LocationRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service responsible for retrieving and managing location data from the database.
 * This service provides a list of available locations for filtering purposes in the UI
 * and handles CRUD operations with business logic validation.
 * Adheres to the Business Logic Layer (PS-02) by abstracting database access for locations.
 */
public class LocationService {

    private static final Logger LOGGER = Logger.getLogger(LocationService.class.getName());
    private final LocationDAO locationDAO = new LocationDAO();
    private final PigLocationDAO pigLocationDAO = new PigLocationDAO();

    /**
     * Retrieves all locations from the database, sorted by their ID in ascending order.
     * This method ensures that the UI (e.g., ComboBox) receives a consistent and ordered list.
     *
     * @return A list of {@link LocationRecord} records. Returns an empty list if no locations are found
     *         or if a database error occurs.
     */
    public List<LocationRecord> getAllLocations() {
        List<LocationRecord> locations = new ArrayList<>();
        String sql = "SELECT location_id, location_name FROM Location ORDER BY location_id ASC";

        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                locations.add(new LocationRecord(rs.getInt("location_id"), rs.getString("location_name")));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve all locations from the database.", e);
        }
        return locations;
    }

    /**
     * Creates a new location.
     *
     * @param locationName The name of the new location.
     * @return The ID of the newly created location.
     * @throws SQLException If a database error occurs.
     */
    public int createLocation(String locationName) throws SQLException {
        if (locationName == null || locationName.trim().isEmpty()) {
            throw new IllegalArgumentException("Lokationsnavn kan ikke være tomt.");
        }
        return locationDAO.create(locationName.trim());
    }

    /**
     * Updates an existing location's name.
     *
     * @param locationId The ID of the location to update.
     * @param newName The new name.
     * @return true if updated, false if not found.
     * @throws SQLException If a database error occurs.
     */
    public boolean updateLocation(int locationId, String newName) throws SQLException {
        if (newName == null || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Lokationsnavn kan ikke være tomt.");
        }
        return locationDAO.update(locationId, newName.trim());
    }

    /**
     * Deletes a location if it has no active pigs.
     *
     * @param locationId The ID of the location to delete.
     * @throws IllegalStateException If the location has active pigs.
     * @throws SQLException If a database error occurs.
     * @return true if successfully deleted.
     */
    public boolean deleteLocation(int locationId) throws SQLException {
        if (pigLocationDAO.hasActivePigsInLocation(locationId)) {
            throw new IllegalStateException("Kan ikke slette lokation/boks, da der er aktive grise i den.");
        }
        return locationDAO.delete(locationId);
    }
}
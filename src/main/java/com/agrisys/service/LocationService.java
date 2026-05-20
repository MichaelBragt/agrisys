package com.agrisys.service;

import com.agrisys.DbConnect;
import com.agrisys.datalayer.entity.LocationRecord;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service responsible for retrieving location data from the database.
 * This service provides a list of available locations for filtering purposes in the UI.
 * Adheres to the Business Logic Layer (PS-02) by abstracting database access for locations.
 */
public class LocationService {

    private static final Logger LOGGER = Logger.getLogger(LocationService.class.getName());

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

        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection(); // Get connection from Singleton
        try (PreparedStatement pstmt = conn.prepareStatement(sql); // Use try-with-resources for PreparedStatement and ResultSet
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                locations.add(new LocationRecord(rs.getInt("location_id"), rs.getString("location_name")));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve all locations from the database.", e);
            // In a production system, you might use UIErrorReport.showDatabaseError(e); here,
            // but for a service, logging is often preferred to allow the caller to decide UI impact.
        }
        return locations;
    }
}
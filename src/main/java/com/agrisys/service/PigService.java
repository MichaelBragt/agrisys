package com.agrisys.service;

import com.agrisys.datalayer.dao.PigDAO;
import com.agrisys.model.view.PigSummary;
import java.sql.SQLException;
import java.util.List;

/**
 * Service layer for Pig management.
 * Handles business rules and aggregates data from DAOs.
 * We have this service method because we need to calculate FRC
 * for the data after we have recieved it from the database.
 */
public class PigService {
    private final PigDAO pigDAO;

    public PigService() {
        this.pigDAO = new PigDAO();
    }

    /**
     * Retrieves summary data and performs business logic calculations.
     * 
     * @param locationId The ID of the location to filter by, or null for all pigs.
     * @return List of processed PigSummary objects.
     */
    public List<PigSummary> getPigDashboardData(Integer locationId) {
        try {
            if (locationId == null) {
                return pigDAO.getPigSummaries(); // Fetch all pigs
            } else {
                return pigDAO.getPigSummariesByLocation(locationId); // Fetch pigs for a specific location
            }
        } catch (SQLException e) {
            // Logic for logging (e.g., Log4j) should go here
            return List.of();
        }
    }

    /**
     * This method is now redundant as getPigDashboardData handles both cases.
     * @deprecated Use {@link #getPigDashboardData(Integer)} instead.
     */
    @Deprecated
    public List<PigSummary> getPigsByLocation(int locationId) { return getPigDashboardData(locationId); }
}
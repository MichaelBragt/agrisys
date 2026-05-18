package com.agrisys.service;

import com.agrisys.dao.PigDAO;
import com.agrisys.model.PigSummary;
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
     * @return List of processed PigSummary objects.
     */
    public List<PigSummary> getActivePigDashboardData() {
        try {
            // In a real scenario, we might calculate FCR here 
            // by fetching feed data and weight gain per pig.
            return pigDAO.getPigSummaries();
        } catch (SQLException e) {
            // Logic for logging (e.g., Log4j) should go here
            return List.of();
        }
    }
}
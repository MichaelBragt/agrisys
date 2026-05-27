package com.agrisys.service;

import com.agrisys.datalayer.dao.PigDAO;
import com.agrisys.model.view.PigSummary;
import java.sql.SQLException;
import java.util.List;

/**
 * Servicelag til generel grise- og besætningsstyring.
 * Varetager forretningsregler og koordinerer dataflowet mellem præsentationslaget og databasselaget.
 * * @author Michael Bragt
 * @see "PS-01: Datavisualisering - Aggregering af rådata til overskuelige dashboards"
 * @see "PS-02: Adgangsstyring - Mellemled for sikker datahentning"
 * @see "FR-06: Systemet skal kunne filtrere besætningen i en liste for fokuseret overblik"
 * @see "FR-09: Systemet skal automatisk beregne FCR og Gennemsnitlig Daglig Tilvækst (ADG)"
 * @see "NFR-02: Architecture - Systemet skal opbygges i en lagdelt arkitektur (UI, Logik, Data)"
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
}
package com.agrisys.service;

import com.agrisys.DbConnect;
import com.agrisys.dao.*;
import com.agrisys.model.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Service orchestrator for registering a new pig.
 * Handles multi-table transaction involving Pig, Responder, Assignment, and Location.
 */
public class PigRegistrationService {
    private final PigDAO pigDAO = new PigDAO();
    private final RespondersDAO respondersDAO = new RespondersDAO();
    private final ResponderAssignmentDAO assignmentDAO = new ResponderAssignmentDAO();
    private final PigLocationDAO pigLocationDAO = new PigLocationDAO();

    /**
     * Registers a new pig and its associated hardware/location in one transaction.
     * @param pig The pig entity
     * @param responder The responder entity
     * @param locationId Target location ID
     * @throws SQLException If transaction fails
     */
    public void registerNewPig(PigRecord pig, RespondersRecord responder, int locationId) throws SQLException {
        Connection conn = DbConnect.UNIQUE_CONNECT.getConnection();
        try {
            conn.setAutoCommit(false);

            // 1. Create Pig
            pigDAO.create(conn, pig);

            // 2. Ensure Responder exists and is marked as 'I brug'
            RespondersRecord activeResponder = new RespondersRecord(responder.responderId(), "I brug");
            respondersDAO.createOrUpdate(conn, activeResponder);

            // 3. Create Assignment
            ResponderAssignmentRecord assignment = new ResponderAssignmentRecord(
                null, 
                pig.animalNumber(), 
                responder.responderId(), 
                LocalDateTime.now(), 
                null
            );
            assignmentDAO.create(conn, assignment);

            // 4. Create Initial Location Placement
            PigLocationRecord location = new PigLocationRecord(
                null, 
                pig.animalNumber(), 
                locationId, 
                LocalDateTime.now(), 
                null
            );
            pigLocationDAO.create(conn, location);

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
}
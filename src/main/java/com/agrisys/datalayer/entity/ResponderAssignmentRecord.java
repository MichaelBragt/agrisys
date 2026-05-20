package com.agrisys.datalayer.entity;

import java.time.LocalDateTime;

/**
 * Manages the "Historical Brain" relationship between a Pig and a Responder.
 * 
 * @param assignmentId Primary key for the assignment period.
 * @param animalNumber Foreign key to Pig.
 * @param responderId Foreign key to Responders.
 * @param dateAssigned When the tag was attached.
 * @param dateRemoved When the tag was removed (null if active).
 */
public record ResponderAssignmentRecord(
    Integer assignmentId,
    String animalNumber,
    String responderId,
    LocalDateTime dateAssigned,
    LocalDateTime dateRemoved
) {}
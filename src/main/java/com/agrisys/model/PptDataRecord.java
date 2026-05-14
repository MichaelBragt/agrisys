package com.agrisys.model;

import java.time.LocalDateTime;

/**
 * High-volume sensor data (PPT_Data).
 * 
 * @param pptDataId Primary key.
 * @param assignmentId Link to the specific Responder_Assignment event.
 * @param visitTime Time of measurement.
 * @param pigWeight Weight in kg.
 * @param feedIntake Feed consumed in kg.
 * @param visitDuration Duration in seconds.
 */
public record PptDataRecord(
    Integer pptDataId,
    int assignmentId,
    LocalDateTime visitTime,
    double pigWeight,
    double feedIntake,
    int visitDuration
) {}
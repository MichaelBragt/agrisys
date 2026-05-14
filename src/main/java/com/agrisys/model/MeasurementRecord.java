package com.agrisys.model;

import java.time.LocalDateTime;

/**
 * High-volume sensor data.
 * @param measurementId Primary key.
 * @param taggingId Link to the specific Tagging event.
 * @param timestamp Time of measurement.
 * @param pigWeight Weight in kg.
 * @param feedIntake Feed consumed in kg.
 * @param visitDuration Duration in seconds.
 */
public record MeasurementRecord(
    Integer measurementId,
    int taggingId,
    LocalDateTime timestamp,
    double pigWeight,
    double feedIntake,
    int visitDuration
) {}
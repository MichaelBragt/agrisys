package com.agrisys.model.view;

import java.time.LocalDate;

/**
 * DTO representing a flattened view of Pig data for the dashboard.
 * Uses Java Records for immutability and performance.
 * 
 * @param animalNumber The unique identifier for the pig.
 * @param responderId The active RFID tag assigned to the pig.
 * @param locationId The current physical location ID.
 * @param birthDate Date of birth.
 * @param weight Latest recorded weight in kg.
 * @param fcr Calculated Feed Conversion Ratio (nullable).
 */
public record PigSummary(
    String animalNumber,
    String responderId,
    Integer locationId,
    LocalDate birthDate,
    Double weight,
    Double fcr
) {}
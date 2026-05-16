package com.agrisys.dto;

import java.time.LocalDateTime;

// Primær forfatter: Michael Bragt

/**
 * A decoupled Data Transfer Object representing a single row from the Excel sensor export.
 * This acts as a staging area before the data is distributed to the 3NF database tables.
 * We use record instead of a DTO class
 * records were introduces in Java 16 and simplifies having a object
 * representing a data object
 *
 * @param animalNumber  Unique identifier for the biological pig (A).
 * @param responderId   The physical responder/tag ID (B).
 * @param locationName  The name of the location/station (C).
 * @param visitTime     The timestamp of the measurement event (D).
 * @param visitDuration Duration of the visit in seconds (E).
 * @param pigWeight     The weight of the pig in kg (F).
 * @param feedIntake    The amount of feed consumed in kg (G).
 */
public record ExcelImportDTO(
    String animalNumber,
    String responderId,
    String locationName,
    LocalDateTime visitTime,
    int visitDuration,
    double pigWeight,
    double feedIntake
) {}
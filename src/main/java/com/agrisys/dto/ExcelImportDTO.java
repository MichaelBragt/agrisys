package com.agrisys.dto;

import java.time.LocalDateTime;

/**
 * A decoupled Data Transfer Object representing a single row from the Excel sensor export.
 * This acts as a staging area before the data is distributed to the 3NF database tables.
 *
 * @param animalNumber   The internal animal identification number.
 * @param rfidCode      The raw RFID string from the scanner.
 * @param location      The physical pen or station identifier.
 * @param timestamp     The date and time of the event.
 * @param weight        The recorded weight of the animal.
 * @param feedIntake    The amount of feed consumed.
 * @param visitDuration How long the animal stayed at the station (seconds).
 */
public record ExcelImportDTO(
    String animalNumber,
    String rfidCode,
    String location,
    LocalDateTime timestamp,
    double weight,
    double feedIntake,
    int visitDuration
) {}
package com.agrisys.model.view;

import java.time.LocalDate;

/**
 * Data Transfer Object for the detailed pig view.
 * Encapsulates biological, hardware, and location data.
 *
 * @param animalNumber  Biological ID
 * @param responderId   Current hardware ID
 * @param assignmentId  PK from Responder_Assignment (for history lookup)
 //* @param birthDate     Birth date
 * @param status        Current status (Aktiv, Slagtet, Syg)
 * @param currentWeight Last recorded weight
 * @param fcr           Calculated FCR
 * @param locationName  Current pen/location
 */
public record PigDetailDTO(
    String animalNumber,
    String responderId,
    Integer assignmentId,
    LocalDate birthDate,
    String status,
    double currentWeight,
    double fcr,
    String locationName
) {}
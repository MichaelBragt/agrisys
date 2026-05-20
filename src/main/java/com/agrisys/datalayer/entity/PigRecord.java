package com.agrisys.datalayer.entity;

import java.time.LocalDate;

/**
 * Represents the biological entity.
 * @param animalNumber Unique identification number (Primary Key).
 * @param birthDate The date the pig was born.
 * @param status Current health/lifecycle status.
 */
public record PigRecord(
    String animalNumber, 
    LocalDate birthDate, 
    String status
) {}
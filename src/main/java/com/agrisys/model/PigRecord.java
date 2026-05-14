package com.agrisys.model;

import java.time.LocalDate;

/**
 * Represents the biological entity.
 * @param pigId Internal primary key.
 * @param birthDate The date the pig was born.
 * @param status Current health/lifecycle status.
 */
public record PigRecord(
    Integer pigId, 
    LocalDate birthDate, 
    String status
) {}
package com.agrisys.datalayer.entity;

import java.time.LocalDateTime;

/**
 * Manages the historical placement of a pig in a specific location.
 * This tracks where a pig was at a given time.
 *
 * @param pigLocationId Primary key for this specific placement period.
 * @param animalNumber Foreign key to Pig.
 * @param locationId Foreign key to Location.
 * @param arrivedAt When the pig arrived at this location.
 * @param departedAt When the pig departed this location (null if currently there).
 */
public record PigLocationRecord(
    Integer pigLocationId,
    String animalNumber,
    Integer locationId,
    LocalDateTime arrivedAt,
    LocalDateTime departedAt
) {}
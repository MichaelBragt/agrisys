package com.agrisys.model;

/**
 * Represents a physical location or pen where pigs can be placed.
 * @param locationId Unique database identifier.
 * @param locationName Human-readable name (e.g., 'Sti 102', 'Faresti 1').
 */
public record LocationRecord(
    Integer locationId,
    String locationName
) {}
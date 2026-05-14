package com.agrisys.model;

/**
 * Represents a physical pen or location.
 * @param penId Unique database identifier.
 * @param penName Human-readable name (e.g., 'Sti 102').
 */
public record PenRecord(Integer penId, String penName) {}
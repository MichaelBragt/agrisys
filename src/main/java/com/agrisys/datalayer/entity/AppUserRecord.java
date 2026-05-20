package com.agrisys.datalayer.entity;

/**
 * Immutable representation of an application user.
 * @param userId Unique database identifier.
 * @param username Unique login name.
 * @param password The user's password (hashed).
 * @param userRole Role-Based Access Control identifier ('Landmand' or 'Raadgiver').
 */
public record AppUserRecord(
    Integer userId,
    String username,
    String password, // Note: This is the hashed password, not plain text
    String userRole
) {}
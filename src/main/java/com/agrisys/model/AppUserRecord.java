package com.agrisys.model;

/**
 * Immutable representation of an application user.
 * @param userId Unique database identifier.
 * @param username Unique login name.
 * @param passwordHash Securely hashed password.
 * @param userRole Role-Based Access Control identifier ('Landmand' or 'Raadgiver').
 */
public record AppUserRecord(Integer userId, String username, String passwordHash, String userRole) {}
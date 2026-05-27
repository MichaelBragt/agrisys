package com.agrisys.datalayer.entity;

/**
 * Enterprise Entity (Data Record) der repræsenterer en registreret systembruger.
 * Bærer de kryptografisk beskyttede legitimationsoplysninger og RBAC-rettigheder i RAM under en aktiv session.
 * * @author Michael Bragt (med kommentarer af gruppen)
 * @see "PS-02: Adgangsstyring - Autentificering og Role-Based Access Control (RBAC)"
 * @see "FR-08: Brugere (landmand/rådgiver) skal kunne logge ind med unikt login"
 * @see "FR-15: Rådgiver-interfacet skal være begrænset til Read-only på grisens stamdata"
 */
public record AppUserRecord(
    Integer userId,
    String username,
    String password, // Note: This is the hashed password, not plain text
    String userRole
) {}
package com.agrisys.datalayer.entity;

/**
 * Enterprise Entity (Data Record) der repræsenterer et fysisk RFID-øremærke (en responder).
 * Holder styr på hardwarens aktuelle tilstand i stalden (f.eks. 'I brug', 'Ledig' eller 'Defekt').
 * * @author Michael Bragt (med kommentarer af gruppen)
 * @see "PS-02: Adgangsstyring - Allokering og kontrol af fysiske hardware-komponenter"
 * @see "Domain Rule 1.2: Filtrering af uallokerede, ledige respondere til nye grise"
 */

public record RespondersRecord(
    String responderId,
    String status
) {}
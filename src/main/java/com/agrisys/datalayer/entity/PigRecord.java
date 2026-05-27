package com.agrisys.datalayer.entity;

import java.time.LocalDate;

/**
 * Enterprise Entity (Data Record) der repræsenterer en biologisk gris i systemet.
 * Denne klasse er uforanderlig (immutable jf. Java Record-standarden) for at garantere dataintegritet i RAM.
 * * @author Michael Bragt
 * @see "PS-03: Interoperabilitet - Persistent datamodel for løbende stald- og besætningsdrift"
 * @see "FR-02: Landmanden skal kunne oprette/indsætte en ny gris med stamdata"
 * @see "NFR-02: Architecture - En del af datalaget (Entity layer) fuldstændig isoleret fra UI-logik"
 */
public record PigRecord(
    String animalNumber, 
    LocalDate birthDate,
    String status
) {}
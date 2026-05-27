package com.agrisys.datalayer.entity;

import java.time.LocalDateTime;

/**
 * Historisk tidsstempels-entity der sporer en specifik gris' ophold i en boks over tid.
 * Fungerer som det datamæssige fundament for staldens "historiske hjerne".
 * * @author Michael Bragt
 * @see "PS-03: Interoperabilitet - Sikring af stabil og persistent historikstyring under flytninger"
 * @see "FR-03: Landmanden skal kunne rette lokation og spore historiske skift"
 */

/**

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
package com.agrisys.datalayer.entity;

/**
 * Enterprise Entity (Data Record) der repræsenterer en fysisk sti, boks eller lokation i stalden.
 * Anvendes til at binde grisenes ophold og målinger op på specifikke geografiske stald-segmenter.
 * * @author Michael Bragt
 * @see "PS-03: Interoperabilitet - Strukturering af staldens fysiske lokationsdata"
 * @see "FR-19: CRUD Båse - Landmanden skal kunne oprette og administrere båse/bokse"
 */
public record LocationRecord(
    Integer locationId,
    String locationName
) {}
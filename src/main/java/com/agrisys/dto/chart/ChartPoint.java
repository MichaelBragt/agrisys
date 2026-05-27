package com.agrisys.dto.chart;

/**
 * Data Transfer Object (DTO) der repræsenterer et enkelt visuelt koordinatpunkt på en graf.
 * X-aksen repræsenterer tidsaksen (f.eks. en dato-streng), og Y-aksen bærer den biologiske måleværdi (vægt, foder, FCR).
 * * @author Eirik (og gruppen)
 * @see "PS-01: Datavisualisering - Standardisering af rå målepunkter til graf-kompatible strukturer"
 * @see "FR-10: Systemet skal præsentere vækst- og foderdata via et Vækst Dashboard med grafer"
 * @see "NFR-02: Architecture - Sikring af lagdelt datatransformation (DTO) mellem DAO og UI"
 */
public record ChartPoint(
        String xValue,
        double yValue
) {}
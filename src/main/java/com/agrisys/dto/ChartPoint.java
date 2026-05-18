package com.agrisys.dto;

/**
 * Repræsenterer et enkelt datapunkt på en graf.
 * X er altid en tekst (f.eks. dato "2026-04-17" eller klokkeslæt), og Y er værdien (vægt, foder, FCR).
 */
public record ChartPoint(
        String xValue,
        double yValue
) {}
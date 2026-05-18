package com.agrisys.dto;

import java.util.List;

/**
 * Repræsenterer en hel linje på en graf (f.eks. "Gris #211863" eller "Boks 2").
 */
public record ChartSeriesData(
        String seriesName,
        List<ChartPoint> points
) {}

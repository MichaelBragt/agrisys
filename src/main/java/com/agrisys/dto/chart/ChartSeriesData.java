package com.agrisys.dto.chart;

import java.util.List;

/**
 * Data Transfer Object (DTO) der repræsenterer en komplet dataserie (en linje/kurve) på et LineChart.
 * Indkapsler seriens navn samt en samling af tilhørende koordinatpunkter til asynkron rendering i UI'et.
 * * @author Eirik (og gruppen)
 * @see "PS-01: Datavisualisering - Aggregering af historiske måleforløb til populations- og enkeltdyrstrends"
 * @see "FR-05: Systemet skal vise vægt- og spiseaktivitet for en specifik gris via kurveforløb"
 * @see "FR-10: Systemet skal præsentere vækst- og foderdata via et Vækst Dashboard med grafer"
 */

public record ChartSeriesData(
        String seriesName,
        List<ChartPoint> points
) {}

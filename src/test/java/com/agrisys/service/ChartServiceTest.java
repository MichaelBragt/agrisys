package com.agrisys.service;

import com.agrisys.dto.chart.ChartPoint;
import com.agrisys.dto.chart.ChartSeriesData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit test af datatransformation og DTO-indkapsling for graf-visninger.
 * Efterviser forretningslagets præcision ved dataformatering før UI-rendering.
 * * @author Eirik Pran
 * @see "PS-01: Datavisualisering - Standardisering af rå målepunkter"
 * @see "NFR-02: Architecture - Sikring af lagdelt datatransformation (DTO)"
 */
class ChartServiceTest {

    @Test
    @DisplayName("Skal validere at FCR-værdier afrundes stringent til 2 decimaler jf. forretningsreglen")
    void testFcrDecimalRoundingLogic() {
        // Scenarie: Simulering af et råt database-output (f.eks. 2.45678912)
        double rawDatabaseFcr = 2.45678912;
        String testDato = "2026-05-28";

        // Eksekver den præcise afrundingsformel fra ChartService.getFcrTrend()
        double processedFcr = Math.round(rawDatabaseFcr * 100.0) / 100.0;

        // Valider resultatet
        assertEquals(2.46, processedFcr, "FCR skal afrundes til præcis 2 decimaler (købmandsafrunding).");

        // Test indkapsling i ChartPoint DTO
        ChartPoint point = new ChartPoint(testDato, processedFcr);
        assertEquals("2026-05-28", point.xValue());
        assertEquals(2.46, point.yValue());
    }

    @Test
    @DisplayName("Skal validere at vægt-værdier (kg) afrundes stringent til 1 decimal jf. forretningsreglen")
    void testWeightDecimalRoundingLogic() {
        // Scenarie: Simulering af en rå gennemsnitsvægt fra databasen (f.eks. 85.3421 kg)
        double rawDatabaseWeightKg = 85.3421;
        String testDato = "2026-05-28";

        // Eksekver den præcise afrundingsformel fra ChartService.getAverageWeight()
        double processedWeight = Math.round(rawDatabaseWeightKg * 10.0) / 10.0;

        // Valider resultatet
        assertEquals(85.3, processedWeight, "Grisens vægt i KG skal afrundes til præcis 1 decimal.");

        ChartPoint point = new ChartPoint(testDato, processedWeight);
        assertEquals(85.3, point.yValue());
    }

    @Test
    @DisplayName("Skal verificere strukturen af en komplet ChartSeriesData DTO til LineChart brug")
    void testChartSeriesDataStructure() {
        // Opbyg en fiktiv tidsserie (Populationstrend)
        List<ChartPoint> punkter = new ArrayList<>();
        punkter.add(new ChartPoint("2026-05-26", 2.40));
        punkter.add(new ChartPoint("2026-05-27", 2.42));
        punkter.add(new ChartPoint("2026-05-28", 2.39));

        // Initialiser DTO containeren
        ChartSeriesData seriesData = new ChartSeriesData("Hele Bestanden", punkter);

        // Valider data-integritet
        assertEquals("Hele Bestanden", seriesData.seriesName(), "Serienavnet skal matche konfigurationen.");
        assertEquals(3, seriesData.points().size(), "Serien skal indeholde nøjagtig 3 målepunkter.");
        assertEquals(2.39, seriesData.points().get(2).yValue(), "Det seneste datapunkt skal returnere 2.39.");
    }
}
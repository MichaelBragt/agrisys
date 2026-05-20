package com.agrisys.Utils;

import com.agrisys.dto.chart.ChartPoint;
import com.agrisys.dto.chart.ChartSeriesData;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import java.util.List;

public final class AgrisysChartBuilder {

    private AgrisysChartBuilder() {}

    /**
     * Generisk metode til at bygge et LineChart med tilpassede mouseover Tooltips.
     * Note: unitLabel styrer hvad der står foran tallet ved mouseover (f.eks. "FCR" eller "Vægt").
     */
    public static LineChart<String, Number> buildLineChart(
            String chartTitle,
            String xAxisLabel,
            String yAxisLabel,
            String unitLabel, // NY PARAMETER HER!
            List<ChartSeriesData> seriesList) {

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel(xAxisLabel);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yAxisLabel);
        yAxis.setAutoRanging(true);
        yAxis.setForceZeroInRange(true);

        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle(chartTitle);
        lineChart.setLegendVisible(false); // Removes the 'button-like' legend below the chart
        lineChart.setAnimated(false);

        for (ChartSeriesData seriesData : seriesList) {
            XYChart.Series<String, Number> fxSeries = new XYChart.Series<>();
            fxSeries.setName(seriesData.seriesName());

            for (ChartPoint point : seriesData.points()) {
                fxSeries.getData().add(new XYChart.Data<>(point.xValue(), point.yValue()));
            }

            lineChart.getData().add(fxSeries);

            Platform.runLater(() -> {
                for (XYChart.Data<String, Number> dataPoint : fxSeries.getData()) {
                    Node node = dataPoint.getNode();

                    if (node != null) {
                        // Vi bruger unitLabel dynamisk her i stedet for hårdtkodet "FCR"
                        String tooltipTekst = String.format("Dato: %s\n%s: %.2f",
                                dataPoint.getXValue(),
                                unitLabel, // Dynamisk label (f.eks. "Vægt" eller "FCR")
                                dataPoint.getYValue().doubleValue()
                        );

                        Tooltip tooltip = new Tooltip(tooltipTekst);
                        tooltip.setShowDelay(Duration.millis(50));
                        tooltip.setShowDuration(Duration.INDEFINITE);
                        tooltip.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-background-color: #1a5276; -fx-text-fill: white;");

                        Tooltip.install(node, tooltip);

                        node.setOnMouseEntered(e -> {
                            node.setStyle("-fx-scale-x: 1.6; -fx-scale-y: 1.6; -fx-cursor: hand;");
                        });

                        node.setOnMouseExited(e -> {
                            node.setStyle("-fx-scale-x: 1.0; -fx-scale-y: 1.0;");
                        });
                    }
                }
            });
        }

        return lineChart;
    }
}
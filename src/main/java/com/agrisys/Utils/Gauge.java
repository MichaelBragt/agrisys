package com.agrisys.Utils;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

// Primær forfatter: Michael Bragt

/**
 * Class for creating an instance that can visually
 * show a Gauge meter
 * With the ability to set the radius and a number for how
 * much the gauge is filled
 * @author Michael Bragt & Eirik
 */
public class Gauge extends StackPane {

    private final Arc statusArc;
    private final Text valueText;
    private final Text statusLabel;

    /**
     * Constructor for setting up the gauge insyance
     * @param radius
     */
    public Gauge(double radius) {
        double thickness = radius * 0.20;
        double totalSize = (radius * 2) + thickness;

        this.setMinSize(totalSize, totalSize);
        this.setPrefSize(totalSize, totalSize);
        this.setMaxSize(totalSize, totalSize);

        Circle backgroundTrack = new Circle(radius, radius, radius);
        backgroundTrack.setFill(Color.TRANSPARENT);
        backgroundTrack.setStroke(Color.web("#E0E0E0"));
        backgroundTrack.setStrokeWidth(thickness);

        statusArc = new Arc();
        statusArc.setCenterX(radius);
        statusArc.setCenterY(radius);
        statusArc.setRadiusX(radius);
        statusArc.setRadiusY(radius);
        statusArc.setStartAngle(90);
        statusArc.setType(ArcType.OPEN);
        statusArc.setFill(Color.TRANSPARENT);
        statusArc.setStrokeWidth(thickness);

        Group shapeLayer = new Group(backgroundTrack, statusArc);

        valueText = new Text("N/A");
        valueText.setFont(Font.font("Arial", radius * 0.40));

        statusLabel = new Text("Venter...");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, radius * 0.22));

        VBox textContainer = new VBox(2, valueText, statusLabel);
        textContainer.setAlignment(Pos.CENTER);

        this.getChildren().addAll(shapeLayer, textContainer);

        // Setter en standard starttilstand
        setFcrValue(0.0);
    }

    /**
     * Oppdaterer gaugen dynamisk basert på en biologisk FCR-verdi (typisk 1.5 til 4.5)
     */
    /**
     * Updates the gauge dynamically based on a biological FCR value.
     * Provides a 5-tier classification system with fluent color fading.
     * * @param fcr The calculated Feed Conversion Ratio (typically 1.5 to 4.5).
     */
    public void setFcrValue(double fcr) {
        if (fcr <= 0) {
            valueText.setText("N/A");
            statusLabel.setText("INGEN DATA");
            statusArc.setLength(0);
            statusArc.setStroke(Color.GRAY);
            statusLabel.setFill(Color.GRAY);
            return;
        }

        // 1. Display the formatted raw value in the center
        valueText.setText(String.format("%.2f", fcr));
        statusLabel.setTextAlignment(TextAlignment.CENTER);

        // 2. Map FCR to arc length (1.5 is perfect/full arc, 4.5 is critical/empty arc)
        double clampedFcr = Math.max(1.5, Math.min(4.5, fcr));
        double goodnessFactor = (4.5 - clampedFcr) / (4.5 - 1.5);
        double targetLength = -goodnessFactor * 360.0;
        statusArc.setLength(targetLength);

        // 3. Define professional color anchors for the interpolation
        Color colorExcellent = Color.web("#107C41"); // Mørkegrøn (Elite performance)
        Color colorGood = Color.web("#34C759");      // Lysegrøn (Optimal standard)
        Color colorAverage = Color.web("#FFCC00");   // Gul (Gennemsnitlig / Hold øje)
        Color colorWarning = Color.web("#FF9500");   // Orange (Forhøjet foderudgift)
        Color colorCritical = Color.web("#FF3B30");  // Rød (Kritisk tab / Sygdomstegn)

        Color dynamicColor;

        // 4. Multi-stage color fading and expanded biological wording
        if (fcr < 2.20) {
            // Excellent: From 1.5 to 2.20 (Fade between Excellent and Good)
            double t = (2.20 - fcr) / (2.20 - 1.5);
            dynamicColor = colorGood.interpolate(colorExcellent, Math.max(0, Math.min(1, t)));
            statusLabel.setText("FCR\nEXCELLENT");
        }
        else if (fcr < 2.50) {
            // Good: From 2.20 to 2.50 (Fade between Good and Average)
            double t = (2.50 - fcr) / (2.50 - 2.20);
            dynamicColor = colorAverage.interpolate(colorGood, Math.max(0, Math.min(1, t)));
            statusLabel.setText("FCR\nOPTIMAL");
        }
        else if (fcr < 2.80) {
            // Average: From 2.50 to 2.80 (Fade between Average and Warning)
            double t = (2.80 - fcr) / (2.80 - 2.50);
            dynamicColor = colorWarning.interpolate(colorAverage, Math.max(0, Math.min(1, t)));
            statusLabel.setText("FCR\nACCEPTABEL");
        }
        else if (fcr < 3.20) {
            // Warning: From 2.80 to 3.20 (Fade between Warning and Critical)
            double t = (3.20 - fcr) / (3.20 - 2.80);
            dynamicColor = colorCritical.interpolate(colorWarning, Math.max(0, Math.min(1, t)));
            statusLabel.setText("FCR\nFORHØJET");
        }
        else {
            // Critical: 3.20 and above (Solid Critical Red)
            dynamicColor = colorCritical;
            statusLabel.setText("FCR\nKRITISK");
        }

        // 5. Apply the synchronized color to both the arc and the label
        statusArc.setStroke(dynamicColor);
        statusLabel.setFill(dynamicColor);
    }
}
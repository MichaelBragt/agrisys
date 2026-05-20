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
    public void setFcrValue(double fcr) {
        if (fcr <= 0) {
            valueText.setText("N/A");
            statusLabel.setText("INGEN DATA");
            statusArc.setLength(0);
            statusArc.setStroke(Color.GRAY);
            return;
        }

        // 1. Vis verdien med 2 desimaler i midten
        valueText.setText(String.format("%.2f", fcr));
        statusLabel.setTextAlignment(TextAlignment.CENTER);

        // 2. Map FCR-verdien til en prosentbue (1.5 er "perfekt/full", 4.5 er "kritisk/tom")
        // Vi klamper verdien mellom 1.5 og 4.5 så buen ikke går amok
        double clampedFcr = Math.max(1.5, Math.min(4.5, fcr));

        // Beregn en faktor fra 0.0 (dårligst) til 1.0 (best)
        double goodnessFactor = (4.5 - clampedFcr) / (4.5 - 1.5);

        // Gjør faktoren om til grader på sirkelen (fra 0 til -360 grader)
        double targetLength = -goodnessFactor * 360.0;
        statusArc.setLength(targetLength);

        // 3. DYNAMISK FARGE-FADE (Interpolation)
        Color godFcrFarge = Color.web("#34C759"); // Grønn
        Color middelsFcrFarge = Color.web("#FFCC00"); // Gul
        Color daarligFcrFarge = Color.web("#FF3B30"); // Rød

        Color dynamiskFarge;

        if (goodnessFactor > 0.5) {
            // Fade mellom Gul (0.5) og Grønn (1.0)
            double t = (goodnessFactor - 0.5) * 2.0;
            dynamiskFarge = middelsFcrFarge.interpolate(godFcrFarge, t);
            statusLabel.setText("FCR\nOPTIMAL");
        } else {
            // Fade mellom Rød (0.0) og Gul (0.5)
            double t = goodnessFactor * 2.0;
            dynamiskFarge = daarligFcrFarge.interpolate(middelsFcrFarge, t);
            statusLabel.setText("FCR\nKRITISK");
        }

        // Push fargen til både buen og tekst-labelen
        statusArc.setStroke(dynamiskFarge);
        statusLabel.setFill(dynamiskFarge);
    }
}
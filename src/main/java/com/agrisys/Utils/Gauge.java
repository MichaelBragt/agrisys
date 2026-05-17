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
import javafx.scene.text.Text;

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
        double thickness = radius * 0.15;
        double totalSize = (radius * 2) + thickness;

        // 1. Maintain the outer bounds so it plays nice in FXML
        this.setMinSize(totalSize, totalSize);
        this.setPrefSize(totalSize, totalSize);
        this.setMaxSize(totalSize, totalSize);

        // 2. Background Track - Explicitly define the center at (radius, radius)
        Circle backgroundTrack = new Circle(radius, radius, radius);
        backgroundTrack.setFill(Color.TRANSPARENT);
        backgroundTrack.setStroke(Color.web("#E0E0E0"));
        backgroundTrack.setStrokeWidth(thickness);

        // 3. Status Arc - Explicitly lock the center to (radius, radius)
        statusArc = new Arc();
        statusArc.setCenterX(radius); // Locked X coordinate
        statusArc.setCenterY(radius); // Locked Y coordinate
        statusArc.setRadiusX(radius);
        statusArc.setRadiusY(radius);
        statusArc.setStartAngle(90);
        statusArc.setType(ArcType.OPEN);
        statusArc.setFill(Color.TRANSPARENT);
        statusArc.setStrokeWidth(thickness);

        // We group the circle and the arch to be able to consistens place them
        // on top of eachother so we can make the coloring in the gauge
        // depending on how "filled" it is
        Group shapeLayer = new Group(backgroundTrack, statusArc);

        // 5. Central Text elements
        valueText = new Text("Ingen Måling");
        valueText.setFont(Font.font("Arial", radius * 0.4));

        statusLabel = new Text("N/A");
        statusLabel.setFont(Font.font("Arial", radius * 0.18));

        VBox textContainer = new VBox(2, valueText, statusLabel);
        textContainer.setAlignment(Pos.CENTER);

        // 6. Add the fused shape Group and the Text box to the StackPane
        // The StackPane will perfectly center the Group as one unit.
        this.getChildren().addAll(shapeLayer, textContainer);

        // Initialize state
        updateStatus(0);
    }

    /**
     * Method for setting and updating the gauge value
     * @param value
     */
    public void updateStatus(double value) {
        double clampedValue = Math.max(0, Math.min(100, value));
        double targetLength = -(clampedValue / 100.0) * 360.0;
        statusArc.setLength(targetLength);

        valueText.setText(String.format("%.0f%%", clampedValue));

        if (clampedValue < 40) {
            statusArc.setStroke(Color.web("#FF3B30"));
            statusLabel.setText("CRITICAL");
            statusLabel.setFill(Color.web("#FF3B30"));
        } else if (clampedValue < 75) {
            statusArc.setStroke(Color.web("#FFCC00"));
            statusLabel.setText("WARNING");
            statusLabel.setFill(Color.web("#FFCC00"));
        } else {
            statusArc.setStroke(Color.web("#34C759"));
            statusLabel.setText("OPTIMAL");
            statusLabel.setFill(Color.web("#34C759"));
        }
    }
}
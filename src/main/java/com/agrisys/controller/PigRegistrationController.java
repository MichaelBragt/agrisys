package com.agrisys.controller;

import com.agrisys.datalayer.dao.LocationDAO;
import com.agrisys.datalayer.dao.RespondersDAO;
import com.agrisys.datalayer.entity.LocationRecord;
import com.agrisys.datalayer.entity.PigRecord;
import com.agrisys.datalayer.entity.RespondersRecord;
import com.agrisys.service.PigRegistrationService;
import com.agrisys.Utils.UIErrorReport;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.sql.SQLException;
import javafx.util.StringConverter;


/**
 * Controller managing the registration wizard for new pigs.
 * Handles validation and coupling of physical RFID responders to animal records.
 * * @author [Gruppemedlem]
 * @see "Requirement 2.2 (UC): Oprette og indsætte en gris"
 * @see "Domain Rule 1.2: Allokering af 15-cifret RFID-øremærke (Responder ID) til dyrenummer"
 */


public class PigRegistrationController {
    @FXML private TextField animalNumberField, newResponderField;
    @FXML private DatePicker birthDatePicker;
    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<LocationRecord> locationCombo;
    @FXML private ComboBox<RespondersRecord> responderCombo;
    @FXML private CheckBox newResponderCheck;
    @FXML private Label newResponderLabel, summaryLabel;
    @FXML private VBox formPane, successPane;

    private final PigRegistrationService service = new PigRegistrationService();
    private final RespondersDAO respondersDAO = new RespondersDAO();
    private final LocationDAO locationDAO = new LocationDAO();

    @FXML
    public void initialize() {
        statusCombo.getItems().addAll("Aktiv", "Slagtet", "Syg");
        statusCombo.setValue("Aktiv");
        
        newResponderCheck.selectedProperty().addListener((obs, old, isSelected) -> {
            responderCombo.setDisable(isSelected);
            newResponderField.setVisible(isSelected);
            newResponderLabel.setVisible(isSelected);
        });

        try {
            // Set a StringConverter for the locationCombo to display locationName
            locationCombo.setConverter(new StringConverter<LocationRecord>() {
                @Override
                public String toString(LocationRecord location) {
                    // Display only the locationName in the ComboBox
                    return location != null ? location.locationName() : "";
                }

                @Override
                public LocationRecord fromString(String string) {
                    // This method is used when the user types into the ComboBox.
                    // For selection from a predefined list, it's often not needed.
                    return null;
                }
            });

            // Set a StringConverter for the responderCombo to display responderId
            responderCombo.setConverter(new StringConverter<RespondersRecord>() {
                @Override
                public String toString(RespondersRecord responder) {
                    // Display only the responderId in the ComboBox
                    return responder != null ? responder.responderId() : "";
                }

                @Override
                public RespondersRecord fromString(String string) {
                    // Not needed for this use case
                    return null;
                }
            });
            locationCombo.getItems().addAll(locationDAO.findAll());
            responderCombo.getItems().addAll(respondersDAO.findAllAvailable());
        } catch (SQLException e) {
            UIErrorReport.showDatabaseError(e);
        }
    }

    @FXML
    private void handleSave() {
        String animalNum = animalNumberField.getText();
        if (!animalNum.matches("\\d{6}")) {
            UIErrorReport.showAlert("Valideringsfejl", "Ugyldigt Dyre Nr", "Dyre nummer skal være præcis 6 cifre.");
            return;
        }

        String respId;
        if (newResponderCheck.isSelected()) {
            respId = newResponderField.getText();
            if (!respId.matches("\\d{15}")) {
                UIErrorReport.showAlert("Valideringsfejl", "Ugyldigt Responder ID", "Nyt ID skal være 15 cifre.");
                return;
            }
        } else {
            if (responderCombo.getValue() == null) return;
            respId = responderCombo.getValue().responderId();
        }

        LocationRecord loc = locationCombo.getValue();
        if (loc == null) return;

        try {
            PigRecord pig = new PigRecord(animalNum, birthDatePicker.getValue(), statusCombo.getValue());
            RespondersRecord resp = new RespondersRecord(respId, "I brug");
            
            service.registerNewPig(pig, resp, loc.locationId());
            
            showSuccess(pig, respId);
        } catch (SQLException e) {
            UIErrorReport.showDatabaseError(e);
        }
    }

    private void showSuccess(PigRecord pig, String respId) {
        formPane.setVisible(false);
        successPane.setVisible(true);
        summaryLabel.setText(String.format("Nr: %s\nResponder: %s\nStatus: %s", 
            pig.animalNumber(), respId, pig.status()));
    }

    @FXML private void handleCancel() {
        animalNumberField.getScene().getWindow().hide();
    }
}
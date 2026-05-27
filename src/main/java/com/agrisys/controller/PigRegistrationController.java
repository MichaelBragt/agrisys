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
 * Controller til styring af oprettelsesguiden (guiden til registrering af nye grise).
 * Håndterer datavalidering og den indledende kobling af IoT-hardware til en biologisk record.
 * * @author Michael Bragt
 * @see "PS-01: Datavisualisering - Intuitive dashboards frem for uoverskuelige tabeller"
 * @see "PS-02: Adgangsstyring - Arkitektonisk rollestyring og individuel CRUD"
 * @see "PS-03: Interoperabilitet - Datamigrering og integration"
 * @see "FR-02: Landmanden skal kunne oprette/indsætte en ny gris med stamdata"
 * @see "FR-14: Systemet skal validere Excel-data for dubletter (RFID) under import"
 * @see "Domain Rule 1.2: Allokering av unikt RFID-øremærke (Responder ID) til dyrenummer ved oprettelse"
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

    // Core applikationsservice til grisehåndtering (Lagdelt arkitektur jf. NFR-02)
    private final PigRegistrationService service = new PigRegistrationService();
    private final RespondersDAO respondersDAO = new RespondersDAO();
    private final LocationDAO locationDAO = new LocationDAO();


    /**
     * initialize kaldes automatisk af JavaFX, når registreringsvinduet indlæses i RAM.
     * Her klargøres dropdown-menuer, lyttere og converters.
     */
    @FXML
    public void initialize() {
        // 1. Konfigurer biologiske statusvalg jf. FR-02
        statusCombo.getItems().addAll("Aktiv", "Slagtet", "Syg");
        statusCombo.setValue("Aktiv");
        // 2. Reaktionslytter: Skifter mellem valg af eksisterende hardware eller oprettelse af nyt (PS-02)
        newResponderCheck.selectedProperty().addListener((obs, old, isSelected) -> {
            responderCombo.setDisable(isSelected);
            newResponderField.setVisible(isSelected);
            newResponderLabel.setVisible(isSelected);
        });

        try {
            // 3. StringConverter til lokationer: Sikrer at landmanden ser navnet (f.eks. 'Sti 5') i stedet for et Java-objekt
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

            // 4. StringConverter til respondere: Viser det rå hardware ID i dropdown-menuen
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

            // 5. Hydrer dropdown-menuerne med data live fra MSSQL-databasen (NFR-02 / PS-03)
            locationCombo.getItems().addAll(locationDAO.findAll());
            responderCombo.getItems().addAll(respondersDAO.findAllAvailable());
        } catch (SQLException e) {
            UIErrorReport.showDatabaseError(e);
        }
    }

    /**
     * Håndterer gem-aktionen og udfører datavalidering før oprettelse (FR-02).
     */
    @FXML
    private void handleSave() {
        String animalNum = animalNumberField.getText();
        // FORRETNINGSVALIDERING: Sikrer at dyrenummeret overholder staldens 6-cifrede standard
        if (!animalNum.matches("\\d{6}")) {
            UIErrorReport.showAlert("Valideringsfejl", "Ugyldigt Dyre Nr", "Dyre nummer skal være præcis 6 cifre.");
            return;
        }

        String respId;
        // Håndtering af hardware-allokering (Domain Rule 1.2)
        if (newResponderCheck.isSelected()) {
            respId = newResponderField.getText();
            // HARDWARE-VALIDERING: Sikrer at nyt RFID-øremærke overholder den globale 15-cifrede IoT-standard
            if (!respId.matches("\\d{15}")) {
                UIErrorReport.showAlert("Valideringsfejl", "Ugyldigt Responder ID", "Nyt ID skal være 15 cifre.");
                return;
            }
        } else {
            // Hvis landmanden vælger fra listen, men ikke har markeret noget overhovedet
            if (responderCombo.getValue() == null) return;
            respId = responderCombo.getValue().responderId();
        }

        LocationRecord loc = locationCombo.getValue();
        if (loc == null) return; // En gris skal altid have en lokation/boks jf. FR-19

        try {
            // Opret domæne-entiteter (Records for immutability jf. god praksis)
            PigRecord pig = new PigRecord(animalNum, birthDatePicker.getValue(), statusCombo.getValue());
            RespondersRecord resp = new RespondersRecord(respId, "I brug");
            // 6. Skub transaktionen ned i servicelaget jf. den lagdelte arkitektur (NFR-02)
            service.registerNewPig(pig, resp, loc.locationId());
            // 7. Vis den innovative succes-skærm (Fjerner den kognitive belastning jf. PS-01)
            showSuccess(pig, respId);
        } catch (SQLException e) {
            // Fanger SQL-fejl, herunder Primary Key overtrædelser ifald dyrenummeret eller responderen var en dublet (FR-14)
            UIErrorReport.showDatabaseError(e);
        }
    }
    /**
     * Skifter UI-tilstanden dynamisk for at give landmanden en klar bekræftelse (PS-01).
     */
    private void showSuccess(PigRecord pig, String respId) {
        formPane.setVisible(false);
        successPane.setVisible(true);
        summaryLabel.setText(String.format("Nr: %s\nResponder: %s\nStatus: %s", 
            pig.animalNumber(), respId, pig.status()));
    }
    /**
     * Lukker pop-up vinduet (Annuller-knap jf. FR-18 navigation).
     */
    @FXML private void handleCancel() {
        animalNumberField.getScene().getWindow().hide();
    }
}
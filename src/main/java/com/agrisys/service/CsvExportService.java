package com.agrisys.service;

import com.agrisys.model.view.PigSummary;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;

/**
 * Service ansvarlig for eksport af grisemålinger og stamdata til CSV-filer.
 * Primær forfatter: Eirik (og Maria)
 */
public class CsvExportService {

    /**
     * Tager en liste af grise (PigSummary) og eksporterer dem til en CSV-fil via et Gem-vindue.
     * @param stage Det nuværende JavaFX vindue (bruges til at vise FileChooser ovenpå)
     * @param summaries Listen af grise der skal eksporteres
     */
    public void exportPigSummariesToCsv(Stage stage, List<PigSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            System.out.println("Ingen data at eksportere.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Gem CSV Eksport");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Filer (*.csv)", "*.csv"));

        // Foreslå et standard filnavn baseret på datoen i dag
        fileChooser.setInitialFileName("agrisys_eksport_" + java.time.LocalDate.now() + ".csv");

        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            // Tving UTF-8 med BOM, så Excel åbner danske bogstaver (ø) helt perfekt med det samme
            try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {
                // Skriv CSV Header. Vi bruger semikolon (;), da danske Excel foretrækker det!
                writer.println("DyreNummer;ResponderID;LokationID;Foedselsdato;SenesteVaegtKG;FCR");

                // Skriv data-rækkerne
                for (PigSummary p : summaries) {

                    // Skudsikker håndtering af tal/null-værdier før formatering
                    String vaegtStr = (p.currentWeight() != null) ? String.format(java.util.Locale.US, "%.2f", p.currentWeight()) : "0.00";
                    String fcrStr = (p.fcr() != null) ? String.format(java.util.Locale.US, "%.2f", p.fcr()) : "0.00";
                    String datoStr = (p.birthDate() != null) ? p.birthDate().toString() : "Ukendt";

                    // Vi bruger rene %s overalt nu, da alt er konverteret til strenge på forhånd. Ingen crashes!
                    writer.println(String.format("%s;%s;%s;%s;%s;%s",
                            p.animalNumber() != null ? p.animalNumber() : "",
                            p.responderId() != null ? p.responderId() : "Ingen",
                            p.locationId() != null ? p.locationId().toString() : "Ikke tildelt",
                            datoStr,
                            vaegtStr,
                            fcrStr
                    ));
                }

                writer.flush();
                System.out.println("CSV-fil blev eksporteret succesfuldt til: " + file.getAbsolutePath());

            } catch (Exception e) {
                System.err.println("Fejl under skrivning af CSV fil: " + e.getMessage());
                e.printStackTrace(); // Gør det muligt at se fejlen i konsollen hvis noget driller
            }
        }
    }
}
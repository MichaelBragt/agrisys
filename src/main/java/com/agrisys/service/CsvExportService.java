package com.agrisys.service;

import com.agrisys.model.view.PigSummary;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;

/**
 * Service ansvarlig for formatering og eksport af konsoliderede besætningsdata til CSV-filer.
 * Håndterer regional separator-tilpasning og tegnsæt-kodning for fejlfri Excel-integration.
 * * @author Eirik (og Maria)
 * @see "PS-04: Rapportering - Fokus på automatisering af PDF- og CSV-output"
 * @see "FR-07: Administration skal kunne eksportere analyser og data til en CSV-fil"
 */

public class CsvExportService {

    /**
     * Tager en filtreret liste af grise (PigSummary) og eksporterer dem til en Excel-optimeret CSV-fil via en SaveDialog.
     * * @param stage Det nuværende JavaFX vindue (anvendes som modal-ejer for FileChooser)
     * @param summaries Listen af griserecords der skal persisteres til filen
     */
    public void exportPigSummariesToCsv(Stage stage, List<PigSummary> summaries) {
        // 1. Forretningsvalidering: Afbryd hvis der overhovedet ikke er noget data at gemme
        if (summaries == null || summaries.isEmpty()) {
            System.out.println("Ingen data at eksportere.");
            return;
        }
        // 2. Initialiser JavaFX FileChooser komponent til filhåndtering (Opfylder FR-18 navigation)
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Gem CSV Eksport");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Filer (*.csv)", "*.csv"));

        // Foreslå et standard filnavn baseret på dags dato jf. god administrativ praksis
        fileChooser.setInitialFileName("agrisys_eksport_" + java.time.LocalDate.now() + ".csv");
        // Åbn operativsystemets "Gem som..." dialogboks
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            // Tving UTF-8 med BOM, så Excel åbner danske bogstaver (ø) helt perfekt med det samme
            try (PrintWriter writer = new PrintWriter(file, "UTF-8")) {

                // =========================================================================
                // EXCEL INTEGRATIONS-TRICK (Løser PS-04 / Lokale Windows Indstillinger)
                // =========================================================================

                // Skriv det usynlige UTF-8 BOM-tegn (Byte Order Mark).
                // Dette tvinger Excel til at indlæse filen som UTF-8, så æ, ø og å ikke korrumperes.
                writer.print('\uFEFF');
                // Fortæl Excel direkte, at denne fil anvender semikolon som separator.
                // Dette forhindrer, at alt data mases sammen i én enkelt kolonne.
                writer.println("sep=;");
                // Skriv CSV Header rækken ud
                writer.println("DyreNummer;ResponderID;LokationID;Foedselsdato;SenesteVaegtKG;FCR");

                // Skriv data-rækkerne
                for (PigSummary p : summaries) {

                    // Skudsikker streng-konvertering af talværdier og null-pointere før udskrivning
                    // Vi tvinger Locale.US for at sikre, at kommatal formateres med punktum i rådata, hvis det ønskes,
                    // eller vi lader landmandens separator styre det. Her holdes US-strengformat for ren migrering.
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
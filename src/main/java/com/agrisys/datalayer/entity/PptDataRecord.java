package com.agrisys.datalayer.entity;

import java.time.LocalDateTime;

/**
 * High-Volume Sensor Entity (Data Record) der repræsenterer en rå IoT-måling fra en PPT-teststation.
 * Indeholder præcisionsdata om dyrets vægt, det nøjagtige foderindtag samt besøgsvarigheden ved truget.
 * * @author Michael Bragt (med kommentarer af gruppen)
 * @see "PS-03: Interoperabilitet - Integration, strukturering og persistens af rå IoT-sensordata"
 * @see "FR-01: Systemet skal importere data fra Excel-filer til MSSQL-databasen"
 * @see "FR-09: Systemet skal automatisk beregne FCR og Gennemsnitlig Daglig Tilvækst (ADG)"
 */

public record PptDataRecord(
    Integer pptDataId,
    int assignmentId,
    LocalDateTime visitTime,
    double pigWeight,
    double feedIntake,
    int visitDuration
) {}
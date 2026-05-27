package com.agrisys.datalayer.entity;

import java.time.LocalDateTime;

/**
 * Systemkritisk koblings-entity der sporer livscyklussen for et genanvendeligt RFID-øremærke på et dyr.
 * Sørger for, at historiske IoT-sensormålinger altid kan spores tilbage til det korrekte dyr, selvom mærket genbruges.
 * * @author Michael Bragt
 * @see "PS-03: Interoperabilitet - Håndtering af hardware-allokering uden datatab"
 * @see "Domain Rule 1.2: Historikstyring for hardware-komponenter via tildelings- og afmonteringstidsstempler"
 */
public record ResponderAssignmentRecord(
    Integer assignmentId,
    String animalNumber,
    String responderId,
    LocalDateTime dateAssigned,
    LocalDateTime dateRemoved
) {}
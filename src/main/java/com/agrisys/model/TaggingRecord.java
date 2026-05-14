package com.agrisys.model;

import java.time.LocalDateTime;

/**
 * Manages the "Historical Brain" relationship between a Pig and a Tag.
 * @param taggingId Primary key for this specific assignment period.
 * @param pigId Foreign key to Pig.
 * @param tagId Foreign key to RFID_Tag.
 * @param dateAssigned When the tag was attached.
 * @param dateRemoved When the tag was removed (null if active).
 */
public record TaggingRecord(
    Integer taggingId,
    int pigId,
    int tagId,
    LocalDateTime dateAssigned,
    LocalDateTime dateRemoved
) {}
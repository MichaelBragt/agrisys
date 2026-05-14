package com.agrisys.model;

/**
 * Represents the physical RFID hardware (Responders).
 * 
 * @param responderId The unique string ID/Code of the responder.
 * @param status The current status (I brug, Ledig, Defekt).
 */
public record RespondersRecord(
    String responderId,
    String status
) {}
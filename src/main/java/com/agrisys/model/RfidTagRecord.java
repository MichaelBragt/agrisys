package com.agrisys.model;

/**
 * Represents the physical RFID hardware.
 * @param rfidTagId Internal primary key.
 * @param rfidCode The unique string etched into the physical tag.
 */
public record RfidTagRecord(Integer rfidTagId, String rfidCode) {}
package com.agrisys.model;

/**
 * Repræsenterer en fysisk lokation i stalden.
 * @param id Database ID
 * @param name Navnet på lokationen (f.eks. "Sti 1")
 */
public record Location(int id, String name) {
    @Override
    public String toString() { return name; }
}
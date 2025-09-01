package com.flowsentinel.core.id;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * Immutable identifier for a step within a flow. Compares by value.
 *
 * @author gokalp
 */
public final class StepId {
    private final String value;

    private StepId(String value) {
        this.value = value;
    }

    /**
     * Factory that validates and creates a {@code StepId}.
     *
     * @param value the textual step identifier
     * @return a new {@code StepId}
     * @throws IllegalArgumentException if the value is null or blank
     */
    @JsonCreator
    public static StepId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The stepId cannot be null or blank.");
        }
        return new StepId(value);
    }

    /**
     * Returns the underlying identifier string.
     *
     * @return the identifier string
     */
    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StepId)) return false;
        return value.equals(((StepId) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
package com.flowsentinel.core.context;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

public final class StepId {
    private final String value;

    private StepId(String value) {
        this.value = value;
    }

    @JsonCreator
    public static StepId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The stepId cannot be null or blank.");
        }
        return new StepId(value);
    }

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
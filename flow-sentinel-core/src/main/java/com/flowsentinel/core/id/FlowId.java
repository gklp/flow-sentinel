package com.flowsentinel.core.id;

import java.util.Objects;

/**
 * Value object representing a unique identifier for a flow.
 * <p>
 * This type is immutable and compares by value. Use {@link #of(String)} to
 * validate and create instances.
 * </p>
 *
 * @author gokalp
 */
public final class FlowId {
    private final String value;

    private FlowId(String value) {
        this.value = value;
    }

    /**
     * Creates a new {@code FlowId} after validating the input string.
     *
     * @param value the textual identifier
     * @return a new {@code FlowId}
     * @throws IllegalArgumentException if the value is null or blank
     */
    public static FlowId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The flowId cannot be null or blank.");
        }
        return new FlowId(value);
    }

    /**
     * Returns the underlying identifier string.
     *
     * @return the identifier string
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowId flowId)) return false;
        return value.equals(flowId.value);
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
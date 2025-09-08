package com.flowsentinel.core.context;

import java.util.Objects;

public final class FlowId {
    private final String value;

    private FlowId(String value) {
        this.value = value;
    }

    public static FlowId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The flowId cannot be null or blank.");
        }
        return new FlowId(value);
    }

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
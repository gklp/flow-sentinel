package com.flowsentinel.core.store;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable serialized snapshot of a flow's state.
 *
 * <p>The payload is the engine's full state (typically JSON). The content type
 * is stored to allow future formats. Instances are immutable and safe to share.</p>
 * <p>
 * author gokalp
 */
public record FlowSnapshot(
        String flowId,
        String payload,
        String contentType,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Canonical constructor that enforces basic invariants.
     */
    public FlowSnapshot {
        flowId = normalizeRequired(flowId, "flowId");
        payload = Objects.requireNonNull(payload, "payload must not be null");
        contentType = normalizeRequired(contentType, "contentType");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Convenience factory with {@code contentType="application/json"} and timestamps set to now.
     *
     * @param flowId flow identifier
     * @param json   serialized JSON payload
     * @return new {@link FlowSnapshot}
     */
    public static FlowSnapshot ofJson(String flowId, String json) {
        var now = Instant.now();
        return new FlowSnapshot(flowId, json, "application/json", now, now);
    }

    private static String normalizeRequired(String s, String field) {
        if (s == null) throw new NullPointerException(field + " must not be null");
        var t = s.trim();
        if (t.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return t;
    }

    /**
     * Returns a copy with a new payload and updated timestamp (content type preserved).
     *
     * @param newPayload new serialized state
     * @return updated {@link FlowSnapshot}
     */
    public FlowSnapshot withPayload(String newPayload) {
        return new FlowSnapshot(flowId, Objects.requireNonNull(newPayload, "payload must not be null"),
                contentType, createdAt, Instant.now());
    }
}

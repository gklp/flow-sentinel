package com.flowsentinel.core.store;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable metadata that represents the current state of a flow.
 *
 * <p>Holds high-level status, the last/current step, and a version used for optimistic locking.
 * Timestamps are UTC instants. Instances are immutable and safe to share.</p>
 *
 * <ul>
 *   <li><b>status</b>: engine-level state (e.g., "NEW", "RUNNING", "COMPLETED", "FAILED")</li>
 *   <li><b>step</b>: last completed or current step identifier</li>
 *   <li><b>version</b>: monotonic version for optimistic locking</li>
 * </ul>
 *
 * <p>Use {@link #createNew(String)} to construct an initial meta.</p>
 * <p>
 * author gokalp
 */
public record FlowMeta(
        String flowId,
        String status,
        String step,
        int version,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Canonical constructor that enforces basic invariants.
     */
    public FlowMeta {
        flowId = normalizeRequired(flowId, "flowId");
        status = normalizeRequired(status, "status");
        step = normalizeRequired(step, "step");
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Creates an initial meta for a new flow with sensible defaults.
     * status=NEW, step=INIT, version=0, timestamps=now.
     *
     * @param flowId unique flow identifier
     * @return a new {@link FlowMeta} instance
     */
    public static FlowMeta createNew(String flowId) {
        var now = Instant.now();
        return new FlowMeta(flowId, "NEW", "INIT", 0, now, now);
    }

    private static String normalizeRequired(String s, String field) {
        if (s == null) throw new NullPointerException(field + " must not be null");
        var t = s.trim();
        if (t.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return t;
    }

    /**
     * Returns a copy with an incremented version and updated timestamp.
     * This is a convenience for optimistic-lock flows.
     *
     * @return new {@link FlowMeta} with version+1 and updatedAt=now
     */
    public FlowMeta nextVersion() {
        return new FlowMeta(flowId, status, step, version + 1, createdAt, Instant.now());
    }

    /**
     * Returns a copy with a new status and step (does not change a version).
     *
     * @param newStatus new engine status
     * @param newStep   new step identifier
     * @return updated {@link FlowMeta}
     */
    public FlowMeta withState(String newStatus, String newStep) {
        return new FlowMeta(flowId, normalizeRequired(newStatus, "status"),
                normalizeRequired(newStep, "step"), version, createdAt, Instant.now());
    }
}

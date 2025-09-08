
package com.flowsentinel.core.store;

import com.flowsentinel.core.context.FlowContext;

import java.time.Instant;
import java.util.Objects;

public record FlowMeta(
        FlowContext flowContext,
        String status,
        String step,
        int version,
        Instant createdAt,
        Instant updatedAt
) {

    public FlowMeta {
        Objects.requireNonNull(flowContext, "flowContext must not be null");
        status = normalizeRequired(status, "status");
        step = normalizeRequired(step, "stepId");
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static FlowMeta createNew(FlowContext flowContext) {
        var now = Instant.now();
        return new FlowMeta(flowContext, "NEW", "INIT", 0, now, now);
    }

    public String flowId() {
        return flowContext.flowId();
    }

    public String getPartitionKey() {
        return flowContext.partitionKey();
    }

    public FlowMeta nextVersion() {
        return new FlowMeta(flowContext, status, step, version + 1, createdAt, Instant.now());
    }

    private static String normalizeRequired(String s, String field) {
        if (s == null) throw new NullPointerException(field + " must not be null");
        var t = s.trim();
        if (t.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return t;
    }

}
package com.flowsentinel.core.store;

import com.flowsentinel.core.context.StepId;

import java.util.Map;
import java.util.Objects;

public record FlowSnapshot(
        String flowId,
        StepId stepId,
        boolean isCompleted,
        Map<String, Object> attributes
) {

    public FlowSnapshot(String flowId, StepId stepId, boolean isCompleted, Map<String, Object> attributes) {
        // Validate and normalize flowId
        Objects.requireNonNull(flowId, "flowId cannot be null.");
        String trimmedFlowId = flowId.trim();
        if (trimmedFlowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }

        // Validate and assign other fields
        this.flowId = trimmedFlowId;
        this.stepId = Objects.requireNonNull(stepId, "stepId cannot be null.");
        this.isCompleted = isCompleted;
        this.attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes cannot be null."));
    }
}
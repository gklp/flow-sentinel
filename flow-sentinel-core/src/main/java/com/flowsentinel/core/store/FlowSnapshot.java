package com.flowsentinel.core.store;

import com.flowsentinel.core.id.StepId;
import com.flowsentinel.core.runtime.FlowState;

import java.util.Map;
import java.util.Objects;

/**
 * An immutable, serializable data-transfer-object (DTO) representing the
 * state of a flow instance at a specific moment.
 * <p>
 * This record is the primary artifact used by the {@link FlowStore} for
 * persistence. It captures all necessary runtime data required to reconstruct
 * a {@link FlowState} object and resume a flow's execution.
 *
 * @param flowId      The unique identifier for the flow instance.
 * @param currentStep The {@link StepId} indicating the flow's current position.
 * @param isCompleted A flag indicating whether the flow has reached a terminal state.
 * @param attributes  A map of runtime data associated with the flow.
 */
public record FlowSnapshot(
        String flowId,
        StepId currentStep,
        boolean isCompleted,
        Map<String, Object> attributes
) {
    /**
     * Canonical constructor for {@link FlowSnapshot}.
     * <p>
     * It validates inputs, normalizes the {@code flowId} by trimming whitespace,
     * and creates a defensive copy of the {@code attributes} map to guarantee
     * the record's immutability.
     */
    public FlowSnapshot(String flowId, StepId currentStep, boolean isCompleted, Map<String, Object> attributes) {
        // Validate and normalize flowId
        Objects.requireNonNull(flowId, "flowId cannot be null.");
        String trimmedFlowId = flowId.trim();
        if (trimmedFlowId.isBlank()) {
            throw new IllegalArgumentException("flowId cannot be blank.");
        }

        // Validate and assign other fields
        this.flowId = trimmedFlowId;
        this.currentStep = Objects.requireNonNull(currentStep, "currentStep cannot be null.");
        this.isCompleted = isCompleted;
        this.attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes cannot be null."));
    }
}
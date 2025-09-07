package com.flowsentinel.core.id;

import java.util.Objects;

/**
 * The composite key that uniquely identifies a flow instance.
 * It combines the business process name, the owner's identity, and a unique session identifier.
 *
 * @param flowName The name of the business process (e.g., "moneyTransfer").
 * @param ownerId  The unique identifier of the flow's owner (e.g., customer number). Can be null if the flow is not user-specific.
 * @param flowId   The unique identifier for the specific flow instance (e.g., JTI or session ID).
 */
public record FlowKey(String flowName, String ownerId, String flowId) {

    public FlowKey {
        Objects.requireNonNull(flowName, "flowName cannot be null.");
        Objects.requireNonNull(flowId, "flowId cannot be null.");
        // ownerId can be null for anonymous flows
    }

    /**
     * Alias for flowName to support definition registry lookup.
     * @return the flowName which represents the definition name
     */
    public String definitionName() {  // 🆕 NEW METHOD!
        return flowName;
    }

    /**
     * Creates a string representation suitable for use as a key in a key-value store.
     * Example: "moneyTransfer:customer-12345:abc-def-ghi"
     * Example without an owner: "public-survey:guest:xyz-789"
     *
     * @return A flattened string key.
     */
    public String toStorageKey() {
        if (ownerId == null || ownerId.isBlank()) {
            return flowName + ":anonymous:" + flowId;
        }
        return flowName + ":" + ownerId + ":" + flowId;
    }
}
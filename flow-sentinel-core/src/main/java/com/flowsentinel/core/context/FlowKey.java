package com.flowsentinel.core.context;

import java.util.Objects;

public record FlowKey(String flowName, String ownerId, String flowId) {

    public FlowKey {
        Objects.requireNonNull(flowName, "flowName cannot be null.");
        Objects.requireNonNull(flowId, "flowId cannot be null.");
    }

    public String definitionName() {
        return flowName;
    }
    
    public String toStorageKey() {
        if (ownerId == null || ownerId.isBlank()) {
            return flowName + ":anonymous:" + flowId;
        }
        return flowName + ":" + ownerId + ":" + flowId;
    }
}
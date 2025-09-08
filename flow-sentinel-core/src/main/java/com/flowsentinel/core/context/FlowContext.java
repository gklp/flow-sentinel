package com.flowsentinel.core.context;

public record FlowContext(String flowId, String ownerId, String partitionKey) {

    public static FlowContext anonymous(String flowId) {
        return new FlowContext(flowId, null, null);
    }

    public static FlowContext of(String flowId, String ownerId) {
        return new FlowContext(flowId, ownerId, null);
    }

    public static FlowContext withPartition(String flowId, String ownerId, String partitionKey) {
        return new FlowContext(flowId, ownerId, partitionKey);
    }

}
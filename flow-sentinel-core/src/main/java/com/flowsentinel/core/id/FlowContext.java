package com.flowsentinel.core.id;

/**
 * Represents the complete context for a flow including identity and partitioning information.
 *
 * @param flowId       The unique identifier for the flow instance (e.g., UUID)
 * @param ownerId      An optional identifier for the owner (e.g., customer number, user ID)
 * @param partitionKey The key used for data partitioning (e.g., tenant, shard, region)
 *                     If null, defaults to ownerId for backward compatibility
 */
public record FlowContext(String flowId, String ownerId, String partitionKey) {
    
    /**
     * Backward compatibility constructor
     */
    public FlowContext(String flowId, String ownerId) {
        this(flowId, ownerId, ownerId); // partitionKey = ownerId by default
    }
    
    /**
     * Gets the effective partition key, falling back to ownerId if partitionKey is null
     */
    public String getEffectivePartitionKey() {
        return partitionKey != null ? partitionKey : ownerId;
    }
    
    /**
     * Creates a FlowContext for anonymous flows (no owner/partition)
     */
    public static FlowContext anonymous(String flowId) {
        return new FlowContext(flowId, null, null);
    }
    
    /**
     * Creates a FlowContext with simple user-based partitioning
     */
    public static FlowContext forUser(String flowId, String userId) {
        return new FlowContext(flowId, userId, userId);
    }
    
    /**
     * Creates a FlowContext with custom partitioning
     */
    public static FlowContext withPartition(String flowId, String ownerId, String partitionKey) {
        return new FlowContext(flowId, ownerId, partitionKey);
    }
}
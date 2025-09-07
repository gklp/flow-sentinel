package com.flowsentinel.starter.session;

import com.flowsentinel.core.store.FlowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * High-level service for managing flow sessions and invalidation scenarios.
 * Provides convenient methods for common session management operations like
 * logout, security events, and administrative cleanup.
 */
@Service
public class FlowSessionManager {
    
    private static final Logger log = LoggerFactory.getLogger(FlowSessionManager.class);
    
    private final FlowStore flowStore;
    
    public FlowSessionManager(FlowStore flowStore) {
        this.flowStore = flowStore;
    }
    
    /**
     * Invalidates all flows when user logs out.
     * This ensures no stale flows remain after logout.
     * 
     * @param userId the user identifier
     * @return number of flows invalidated
     */
    public int invalidateUserSession(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId cannot be blank");
        }
        
        int invalidated = flowStore.invalidateByPartition(userId);
        log.info("User logout - invalidated {} flows for user: {}", invalidated, userId);
        return invalidated;
    }
    
    /**
     * Invalidates flows on security events (token revoke, suspicious activity, etc.).
     * Provides audit logging for security compliance.
     * 
     * @param partitionKey the partition key (customerId, tenantId, etc.)
     * @param reason the security event reason
     * @return number of flows invalidated
     */
    public int invalidateOnSecurityEvent(String partitionKey, String reason) {
        if (partitionKey == null || partitionKey.trim().isEmpty()) {
            throw new IllegalArgumentException("partitionKey cannot be blank");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
        
        Set<String> activeFlows = flowStore.listActiveFlows(partitionKey);
        log.warn("Security event [{}] - found {} active flows for partition: {}", 
                 reason, activeFlows.size(), partitionKey);
        
        int invalidated = flowStore.invalidateByPartition(partitionKey);
        
        log.warn("Security event [{}] - invalidated {} flows for partition: {}", 
                 reason, invalidated, partitionKey);
        
        return invalidated;
    }
    
    /**
     * Bulk invalidates specific flows by their IDs.
     * Useful for targeted cleanup operations.
     * 
     * @param flowIds set of flow IDs to invalidate
     * @param reason optional reason for logging
     * @return number of flows actually invalidated
     */
    public int invalidateFlows(Set<String> flowIds, String reason) {
        if (flowIds == null) {
            throw new IllegalArgumentException("flowIds cannot be null");
        }
        
        if (flowIds.isEmpty()) {
            log.debug("No flows to invalidate");
            return 0;
        }
        
        int invalidated = flowStore.bulkDelete(flowIds);
        
        String logReason = reason != null && !reason.trim().isEmpty() ? reason : "manual operation";
        log.info("Bulk invalidation [{}] - invalidated {} flows", logReason, invalidated);
        
        return invalidated;
    }
    
    /**
     * Lists all active flows for a partition.
     * Useful for monitoring and debugging.
     * 
     * @param partitionKey the partition key
     * @return set of active flow IDs
     */
    public Set<String> listActiveFlows(String partitionKey) {
        if (partitionKey == null || partitionKey.trim().isEmpty()) {
            throw new IllegalArgumentException("partitionKey cannot be blank");
        }
        
        Set<String> flows = flowStore.listActiveFlows(partitionKey);
        log.debug("Found {} active flows for partition: {}", flows.size(), partitionKey);
        return flows;
    }
    
    /**
     * Invalidates flows for multiple partitions at once.
     * Useful for tenant-wide or cross-partition cleanup.
     * 
     * @param partitionKeys set of partition keys to invalidate
     * @param reason optional reason for logging
     * @return total number of flows invalidated
     */
    public int invalidateMultiplePartitions(Set<String> partitionKeys, String reason) {
        if (partitionKeys == null) {
            throw new IllegalArgumentException("partitionKeys cannot be null");
        }
        
        if (partitionKeys.isEmpty()) {
            log.debug("No partitions to invalidate");
            return 0;
        }
        
        int totalInvalidated = 0;
        for (String partitionKey : partitionKeys) {
            if (partitionKey != null && !partitionKey.trim().isEmpty()) {
                int invalidated = flowStore.invalidateByPartition(partitionKey);
                totalInvalidated += invalidated;
                log.debug("Invalidated {} flows for partition: {}", invalidated, partitionKey);
            }
        }
        
        String logReason = reason != null && !reason.trim().isEmpty() ? reason : "bulk partition cleanup";
        log.info("Multi-partition invalidation [{}] - total {} flows invalidated across {} partitions", 
                 logReason, totalInvalidated, partitionKeys.size());
        
        return totalInvalidated;
    }
}
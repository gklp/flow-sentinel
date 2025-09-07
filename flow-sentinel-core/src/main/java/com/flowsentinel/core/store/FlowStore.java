package com.flowsentinel.core.store;

import com.flowsentinel.core.id.FlowContext;
import com.flowsentinel.core.runtime.FlowState;

import java.util.Optional;
import java.util.Set;

/**
 * Abstraction for FlowSentinel's persistence operations.
 *
 * <p>All storage implementations (Redis, JDBC, InMemory) must implement this interface.</p>
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Persist and retrieve flow meta-information.</li>
 *   <li>Persist and retrieve serialized flow snapshots.</li>
 *   <li>Delete snapshots and associated meta entries atomically.</li>
 *   <li>Check flow existence efficiently.</li>
 *   <li>Support partition-based operations for multi-tenancy and session management.</li>
 * </ul>
 *
 * @author gokalp
 */
public interface FlowStore {

    /**
     * Persists meta-information about a flow.
     *
     * @param meta must not be null
     * @throws IllegalArgumentException if meta is null or invalid
     */
    void saveMeta(FlowMeta meta);

    /**
     * Loads meta-information for a given flow.
     *
     * @param flowId flow identifier must not be blank
     * @return meta if found, otherwise empty
     * @throws IllegalArgumentException if flowId is blank
     */
    Optional<FlowMeta> loadMeta(String flowId);

    /**
     * Loads meta-information for a given flow context.
     * Implementations may use partition information for optimized lookups.
     *
     * @param context flow context containing flowId and partition information
     * @return meta if found, otherwise empty
     * @throws IllegalArgumentException if context or flowId is invalid
     */
    default Optional<FlowMeta> loadMeta(FlowContext context) {
        return loadMeta(context.flowId());
    }

    /**
     * Persists a serialized snapshot of the flow's current state.
     *
     * @param snapshot must not be null
     * @throws IllegalArgumentException if the snapshot is null or invalid
     */
    void saveSnapshot(FlowSnapshot snapshot);

    /**
     * Loads a serialized snapshot for a given flow.
     *
     * @param flowId flow identifier must not be blank
     * @return snapshot if found, otherwise empty
     * @throws IllegalArgumentException if flowId is blank
     */
    Optional<FlowSnapshot> loadSnapshot(String flowId);

    /**
     * Loads a serialized snapshot for a given flow context.
     * Implementations may use partition information for optimized lookups.
     *
     * @param context flow context containing flowId and partition information
     * @return snapshot if found, otherwise empty
     * @throws IllegalArgumentException if context or flowId is invalid
     */
    default Optional<FlowSnapshot> loadSnapshot(FlowContext context) {
        return loadSnapshot(context.flowId());
    }

    /**
     * Deletes a flow and all its associated data (snapshot and meta-information).
     *
     * @param flowId flow identifier must not be blank
     * @throws IllegalArgumentException if flowId is blank
     */
    void delete(String flowId);

    /**
     * Deletes a flow using context information.
     *
     * @param context flow context containing flowId and partition information
     * @throws IllegalArgumentException if context or flowId is invalid
     */
    default void delete(FlowContext context) {
        delete(context.flowId());
    }

    /**
     * Checks whether a flow exists in the store.
     *
     * @param flowId flow identifier must not be blank
     * @return true if flow exists, false otherwise
     * @throws IllegalArgumentException if flowId is blank
     */
    boolean exists(String flowId);

    /**
     * Checks whether a flow exists using context information.
     *
     * @param context flow context containing flowId and partition information
     * @return true if flow exists, false otherwise
     * @throws IllegalArgumentException if context or flowId is invalid
     */
    default boolean exists(FlowContext context) {
        return exists(context.flowId());
    }

    // ========== SESSION INVALIDATION METHODS ==========

    /**
     * Invalidates all flows for a specific partition key.
     * Useful for logout, security events, session expiry scenarios.
     *
     * @param partitionKey the partition key (userId, customerId, tenantId, etc.)
     * @return number of flows invalidated
     * @throws IllegalArgumentException if partitionKey is blank
     */
    int invalidateByPartition(String partitionKey);

    /**
     * Lists all active flow IDs for a specific partition.
     * Useful for debugging, monitoring, cleanup operations.
     *
     * @param partitionKey the partition key
     * @return set of active flow IDs (empty if none found)
     * @throws IllegalArgumentException if partitionKey is blank
     */
    Set<String> listActiveFlows(String partitionKey);

    /**
     * Bulk delete multiple flows atomically.
     * More efficient than individual delete operations.
     *
     * @param flowIds set of flow IDs to delete
     * @return number of flows actually deleted
     * @throws IllegalArgumentException if flowIds is null
     */
    int bulkDelete(Set<String> flowIds);

    /**
     * Invalidates all flows for a specific owner.
     * Convenience method that uses ownerId as partition key.
     *
     * @param ownerId the owner identifier
     * @return number of flows invalidated
     * @throws IllegalArgumentException if ownerId is blank
     */
    default int invalidateByOwner(String ownerId) {
        return invalidateByPartition(ownerId);
    }
}
package com.flowsentinel.core.store;

import java.util.Optional;

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
     * Deletes a flow and all its associated data (snapshot and meta-information).
     *
     * @param flowId flow identifier must not be blank
     * @throws IllegalArgumentException if flowId is blank
     */
    void delete(String flowId);

    /**
     * Checks whether a flow exists in the store.
     *
     * @param flowId flow identifier must not be blank
     * @return true if flow exists, false otherwise
     * @throws IllegalArgumentException if flowId is blank
     */
    boolean exists(String flowId);
}
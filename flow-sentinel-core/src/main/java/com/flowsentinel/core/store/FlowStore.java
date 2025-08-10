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
 * </ul>
 * <p>
 * author gokalp
 */
public interface FlowStore {

    /**
     * Persists meta-information about a flow.
     *
     * @param meta must not be null
     */
    void saveMeta(FlowMeta meta);

    /**
     * Loads meta-information for a given flow.
     *
     * @param flowId flow identifier
     * @return meta if found, otherwise empty
     */
    Optional<FlowMeta> loadMeta(String flowId);

    /**
     * Persists a serialized snapshot of the flow's current state.
     *
     * @param snapshot must not be null
     */
    void saveSnapshot(FlowSnapshot snapshot);

    /**
     * Loads a serialized snapshot for a given flow.
     *
     * @param flowId flow identifier
     * @return snapshot if found, otherwise empty
     */
    Optional<FlowSnapshot> loadSnapshot(String flowId);

    /**
     * Deletes a snapshot and its associated meta-entry.
     *
     * @param flowId flow identifier
     */
    void deleteSnapshot(String flowId);
}

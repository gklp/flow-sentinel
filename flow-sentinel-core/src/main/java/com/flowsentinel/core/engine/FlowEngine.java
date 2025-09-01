package com.flowsentinel.core.engine;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.id.FlowKey;
import com.flowsentinel.core.runtime.FlowState;

import java.util.Map;
import java.util.Optional;

/**
 * Defines the primary contract for interacting with the flow execution engine.
 * <p>
 * This interface is responsible for managing the lifecycle of a flow instance
 * using a composite {@link FlowKey} to uniquely identify it. It handles starting,
 * advancing, and retrieving flow states.
 *
 * @see DefaultFlowEngine
 * @see FlowState
 * @see FlowKey
 */
public interface FlowEngine {

    /**
     * Starts a new flow instance, persists its initial state, and returns it.
     * The instance is uniquely identified by the provided {@link FlowKey}.
     *
     * @param flowKey           The composite key that uniquely identifies the new flow instance.
     * @param definition        The {@link FlowDefinition} that governs the flow's behavior.
     * @param initialAttributes A map of initial data to populate the flow's state.
     *                          Can be empty.
     * @return The newly created {@link FlowState}.
     * @throws FlowEngineException if a flow with the given key already exists.
     */
    FlowState start(FlowKey flowKey, FlowDefinition definition, Map<String, Object> initialAttributes);

    /**
     * Advances an existing flow instance to its next state based on the current state
     * and the provided payload.
     *
     * @param flowKey    The composite key of the flow instance to advance.
     * @param definition The flow's corresponding {@link FlowDefinition}.
     * @param payload    A map of data to be merged into the flow's state before
     *                   evaluating transitions. Can be empty.
     * @return The updated {@link FlowState} after the transition.
     * @throws FlowEngineException if the flow is not found, is already completed,
     *                             or if no valid transition can be found.
     */
    FlowState advance(FlowKey flowKey, FlowDefinition definition, Map<String, Object> payload);

    /**
     * Retrieves the current state of a specific flow instance from the underlying storage.
     *
     * @param flowKey The composite key of the flow to retrieve.
     * @return An {@link Optional} containing the current {@link FlowState} if the flow
     *         exists, or an empty Optional otherwise.
     */
    Optional<FlowState> getState(FlowKey flowKey);
}
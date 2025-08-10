package com.flowsentinel.core.engine;

import com.flowsentinel.core.runtime.FlowState;

/**
 * Flow engine SPI. Implementations advance a {@link FlowState} according to its
 * backing definition.
 *
 * @author gokalp
 */
public interface FlowEngine {
    /**
     * Advances the given state by applying the current step's transitions.
     *
     * @param state the flow state to advance
     * @throws FlowEngineException      if no transition matches or configuration is invalid
     * @throws IllegalArgumentException if the state is null
     */
    void next(FlowState state);
}
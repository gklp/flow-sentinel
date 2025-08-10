package com.flowsentinel.core.runtime;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.id.StepId;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable state for a flow execution instance. The engine mutates this object
 * to advance through steps in a controlled way. Only a minimal API is exposed.
 *
 * @author gokalp
 */
public final class FlowState {
    private final FlowDefinition definition;
    private StepId currentStep;
    private boolean completed;
    private final Map<String, Object> attributes = new HashMap<>();

    /**
     * Creates a new {@code FlowState} for the given definition, starting at the initial step.
     *
     * @param definition the flow definition
     * @throws NullPointerException if the definition is null
     */
    public FlowState(FlowDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "The definition cannot be null.");
        this.currentStep = definition.initialStep();
        this.completed = false;
    }

    /**
     * Returns the flow definition backing this state.
     *
     * @return the flow definition
     */
    public FlowDefinition definition() {
        return definition;
    }

    /**
     * Returns the current step identifier.
     *
     * @return the current step id
     */
    public StepId currentStep() {
        return currentStep;
    }

    /**
     * Indicates whether the flow has completed.
     *
     * @return {@code true} if the flow is completed
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Writes an attribute used for decisioning or telemetry.
     *
     * @param key   attribute key
     * @param value attribute value (may be null)
     * @throws NullPointerException if the key is null
     */
    public void setAttribute(String key, Object value) {
        Objects.requireNonNull(key, "The attribute key cannot be null.");
        attributes.put(key, value);
    }

    /**
     * Returns an immutable snapshot of the attributes.
     *
     * @return immutable map of attributes
     */
    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Marks this state as completed (engine use only).
     */
    public void markCompleted() {
        this.completed = true;
    }

    /**
     * Moves to the given next step (engine use only).
     */
    public void moveTo(StepId next) {
        this.currentStep = Objects.requireNonNull(next, "The next step cannot be null.");
    }
}
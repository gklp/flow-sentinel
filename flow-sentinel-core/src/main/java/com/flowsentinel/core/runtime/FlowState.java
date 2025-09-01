package com.flowsentinel.core.runtime;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.definition.Transition;
import com.flowsentinel.core.engine.FlowEngine;
import com.flowsentinel.core.id.StepId;
import com.flowsentinel.core.store.FlowSnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the immutable, in-memory state of a single, running flow instance.
 * <p>
 * This class acts as a "live" container for all runtime data related to a flow,
 * including its current position ({@link #currentStep()}), completion status
 * ({@link #isCompleted()}), and a map of dynamic attributes used for evaluating
 * conditional transitions.
 * <p>
 * This object is immutable. Its lifecycle is exclusively managed by a {@link FlowEngine},
 * which creates new instances of the state for each step transition.
 *
 * @see FlowEngine
 * @see FlowSnapshot
 */
public final class FlowState {

    private final FlowDefinition definition;
    private final Map<String, Object> attributes;
    private final StepId currentStep;
    private final Boolean completed;

    /**
     * Private constructor to ensure state is created via controlled factory methods.
     */
    public FlowState(FlowDefinition definition, StepId currentStep, Boolean completed, Map<String, Object> attributes) {
        this.definition = Objects.requireNonNull(definition, "FlowDefinition cannot be null.");
        this.currentStep = Objects.requireNonNull(currentStep, "Current StepId cannot be null.");
        this.completed = completed;
        this.attributes = new HashMap<>(Objects.requireNonNull(attributes, "Attributes map cannot be null."));
    }

    /**
     * Creates the initial state for a new flow instance.
     * The flow starts at the {@link FlowDefinition#initialStep()} and is marked as not completed.
     *
     * @param definition        The definition of the flow to start.
     * @param initialAttributes An optional map of attributes to populate the initial state.
     * @return A new {@link FlowState} instance, ready for execution.
     */
    public static FlowState create(FlowDefinition definition, Map<String, Object> initialAttributes) {
        Objects.requireNonNull(definition, "FlowDefinition cannot be null.");
        Map<String, Object> attributes = (initialAttributes != null) ? new HashMap<>(initialAttributes) : new HashMap<>();
        return new FlowState(definition, definition.initialStep(), false, attributes);
    }

    /**
     * Reconstructs a {@link FlowState} instance from a persisted {@link FlowSnapshot}.
     * This factory method is used by the engine to load a flow's state from storage.
     *
     * @param definition The corresponding {@link FlowDefinition} for this flow.
     * @param snapshot   The persisted data from which to restore state.
     * @return A hydrated {@link FlowState} instance.
     */
    public static FlowState fromSnapshot(FlowDefinition definition, FlowSnapshot snapshot) {
        Objects.requireNonNull(definition, "FlowDefinition cannot be null.");
        Objects.requireNonNull(snapshot, "FlowSnapshot cannot be null.");
        return new FlowState(definition, snapshot.currentStep(), snapshot.isCompleted(), snapshot.attributes());
    }

    /**
     * Creates a new {@link FlowState} representing the state after a transition.
     * <p>
     * This method is the core of the immutable state progression. It takes the current
     * state, a decided transition, and an incoming payload, and returns a completely new
     * {@code FlowState} object representing the result.
     *
     * @param transition The {@link Transition} to apply. Must not be null.
     * @param payload    The payload data from the advance call, which will be merged into the attributes. Can be null.
     * @return A new {@link FlowState} instance.
     */
    public FlowState advance(Transition transition, Map<String, Object> payload) {
        Objects.requireNonNull(transition, "Transition cannot be null.");

        // Create the new attribute map by merging current attributes with the new payload.
        Map<String, Object> newAttributes = new HashMap<>(this.attributes);
        if (payload != null) {
            newAttributes.putAll(payload);
        }

        if (transition.isEndOfFlow()) {
            // If the transition marks the end, create a new state that is completed.
            // The "currentStep" remains the step at which the flow ended.
            return new FlowState(this.definition, this.currentStep, true, newAttributes);
        } else {
            // Otherwise, create a new state pointing to the next step.
            StepId nextStep = transition.to();
            return new FlowState(this.definition, nextStep, false, newAttributes);
        }
    }

    /**
     * Creates a {@link FlowSnapshot} from the current state for persistence.
     *
     * @param flowId The unique identifier of this flow instance.
     * @return A serializable {@link FlowSnapshot} representing the current state.
     */
    public FlowSnapshot toSnapshot(String flowId) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null.");
        return new FlowSnapshot(flowId, this.currentStep, this.isCompleted(), this.attributes);
    }

    /**
     * Returns the static {@link FlowDefinition} that governs this flow instance.
     */
    public FlowDefinition definition() {
        return definition;
    }

    /**
     * Returns the identifier of the current step in the flow.
     */
    public StepId currentStep() {
        return currentStep;
    }

    /**
     * Checks if the flow has reached a terminal state (end-of-flow).
     *
     * @return {@code true} if the flow is completed, {@code false} otherwise.
     */
    public boolean isCompleted() {
        return Boolean.TRUE.equals(completed);
    }

    /**
     * Returns a read-only, unmodifiable view of the flow's runtime attributes.
     *
     * @return An unmodifiable map of attributes.
     */
    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }
}
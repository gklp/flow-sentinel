package com.flowsentinel.core.runtime;

import com.flowsentinel.core.context.StepId;
import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.definition.Transition;
import com.flowsentinel.core.store.FlowSnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class FlowState {

    private final FlowDefinition definition;
    private final Map<String, Object> attributes;
    private final StepId currentStep;
    private final Boolean completed;

    public FlowState(FlowDefinition definition, StepId currentStep, Boolean completed, Map<String, Object> attributes) {
        this.definition = Objects.requireNonNull(definition, "FlowDefinition cannot be null.");
        this.currentStep = Objects.requireNonNull(currentStep, "Current StepId cannot be null.");
        this.completed = completed;
        this.attributes = new HashMap<>(Objects.requireNonNull(attributes, "Attributes map cannot be null."));
    }

    public static FlowState create(FlowDefinition definition, Map<String, Object> initialAttributes) {
        Objects.requireNonNull(definition, "FlowDefinition cannot be null.");
        Map<String, Object> attributes = (initialAttributes != null) ? new HashMap<>(initialAttributes) : new HashMap<>();
        return new FlowState(definition, definition.initialStep(), false, attributes);
    }

    public static FlowState fromSnapshot(FlowDefinition definition, FlowSnapshot snapshot) {
        Objects.requireNonNull(definition, "FlowDefinition cannot be null.");
        Objects.requireNonNull(snapshot, "FlowSnapshot cannot be null.");
        return new FlowState(definition, snapshot.stepId(), snapshot.isCompleted(), snapshot.attributes());
    }

    public FlowState advance(Transition transition, Map<String, Object> payload) {
        Objects.requireNonNull(transition, "Transition cannot be null.");

        // Create the new attribute map by merging current attributes with the new payload.
        Map<String, Object> newAttributes = new HashMap<>(this.attributes);
        if (payload != null) {
            newAttributes.putAll(payload);
        }

        if (transition.isEndOfFlow()) {
            // If the transition marks the end, create a new state that is completed.
            // The "stepId" remains the stepId at which the flow ended.
            return new FlowState(this.definition, this.currentStep, true, newAttributes);
        } else {
            // Otherwise, create a new state pointing to the next stepId.
            StepId nextStep = transition.to();
            return new FlowState(this.definition, nextStep, false, newAttributes);
        }
    }

    public FlowSnapshot toSnapshot(String flowId) {
        Objects.requireNonNull(flowId, "Flow ID cannot be null.");
        return new FlowSnapshot(flowId, this.currentStep, this.isCompleted(), this.attributes);
    }

    public FlowDefinition definition() {
        return definition;
    }

    public StepId currentStep() {
        return currentStep;
    }

    public boolean isCompleted() {
        return Boolean.TRUE.equals(completed);
    }

    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }
}
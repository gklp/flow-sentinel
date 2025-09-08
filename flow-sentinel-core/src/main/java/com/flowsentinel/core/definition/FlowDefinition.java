package com.flowsentinel.core.definition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flowsentinel.core.context.FlowId;
import com.flowsentinel.core.context.StepId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class FlowDefinition {
    private final FlowId id;
    private final StepId initialStep;
    private final Map<StepId, StepDefinition> steps; // insertion-order for readability

    private FlowDefinition(Builder b) {
        this.id = Objects.requireNonNull(b.id, "The id cannot be null.");
        this.initialStep = Objects.requireNonNull(b.initialStep, "The initialStep cannot be null.");
        if (!b.steps.containsKey(b.initialStep)) {
            throw new IllegalArgumentException("The initial stepId must be present in the steps map.");
        }
        this.steps = Collections.unmodifiableMap(new LinkedHashMap<>(b.steps));
    }

    @JsonCreator
    public FlowDefinition(
            @JsonProperty("id") FlowId id,
            @JsonProperty("initialStep") StepId initialStep,
            @JsonProperty("steps") List<StepDefinition> stepsList) {
        this.id = Objects.requireNonNull(id, "The id cannot be null.");
        this.initialStep = Objects.requireNonNull(initialStep, "The initialStep cannot be null.");

        if (stepsList == null) {
            throw new IllegalArgumentException("The steps list cannot be null.");
        }

        // Convert a list to map, using the stepId's ID as the key.
        final Map<StepId, StepDefinition> stepsMap = stepsList.stream()
                .collect(Collectors.toMap(
                        StepDefinition::id,
                        s -> s,
                        (v1, v2) -> v1, // In case of duplicates, keep the first one
                        LinkedHashMap::new)
                );

        if (!stepsMap.containsKey(this.initialStep)) {
            throw new IllegalArgumentException("The initial stepId must be present in the steps map.");
        }
        this.steps = Collections.unmodifiableMap(stepsMap);
    }

    public FlowId id() {
        return id;
    }

    public StepId initialStep() {
        return initialStep;
    }

    public Map<StepId, StepDefinition> steps() {
        return steps;
    }

    public StepDefinition step(StepId id) {
        return steps.get(id);
    }

    public static class Builder {
        private FlowId id;
        private StepId initialStep;
        private final Map<StepId, StepDefinition> steps = new LinkedHashMap<>();

        public Builder id(FlowId id) {
            this.id = id;
            return this;
        }

        public Builder initialStep(StepId initialStep) {
            this.initialStep = initialStep;
            return this;
        }

        public Builder step(StepDefinition step) {
            this.steps.put(step.id(), step);
            return this;
        }

        public Builder steps(Map<StepId, StepDefinition> steps) {
            this.steps.putAll(steps);
            return this;
        }

        public FlowDefinition build() {
            return new FlowDefinition(this);
        }
    }
}
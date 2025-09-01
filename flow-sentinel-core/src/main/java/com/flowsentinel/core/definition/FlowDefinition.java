package com.flowsentinel.core.definition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flowsentinel.core.id.FlowId;
import com.flowsentinel.core.id.StepId;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable aggregate root describing an entire flow: steps and initial step.
 * Use the builder to construct valid instances.
 *
 * @author gokalp
 */
public final class FlowDefinition {
    private final FlowId id;
    private final StepId initialStep;
    private final Map<StepId, StepDefinition> steps; // insertion-order for readability

    private FlowDefinition(Builder b) {
        this.id = Objects.requireNonNull(b.id, "The id cannot be null.");
        this.initialStep = Objects.requireNonNull(b.initialStep, "The initialStep cannot be null.");
        if (!b.steps.containsKey(b.initialStep)) {
            throw new IllegalArgumentException("The initial step must be present in the steps map.");
        }
        this.steps = Collections.unmodifiableMap(new LinkedHashMap<>(b.steps));
    }

    /**
     * Constructor for Jackson deserialization.
     * This creator allows Jackson to construct an instance from a JSON structure where "steps" is an array.
     *
     * @param id          The flow identifier.
     * @param initialStep The identifier of the first step.
     * @param stepsList   A list of step definitions from the JSON array.
     */
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

        // Convert list to map, using the step's ID as the key.
        final Map<StepId, StepDefinition> stepsMap = stepsList.stream()
                .collect(Collectors.toMap(
                        StepDefinition::id,
                        s -> s,
                        (v1, v2) -> v1, // In case of duplicates, keep the first one
                        LinkedHashMap::new)
                );

        if (!stepsMap.containsKey(this.initialStep)) {
            throw new IllegalArgumentException("The initial step must be present in the steps map.");
        }
        this.steps = Collections.unmodifiableMap(stepsMap);
    }


    /**
     * Returns the flow identifier.
     *
     * @return the flow id
     */
    public FlowId id() {
        return id;
    }

    /**
     * Returns the initial step identifier.
     *
     * @return the initial step id
     */
    public StepId initialStep() {
        return initialStep;
    }

    /**
     * Returns all step definitions keyed by their identifier.
     *
     * @return immutable map of steps
     */
    public Map<StepId, StepDefinition> steps() {
        return steps;
    }

    /**
     * Looks up a step definition by its identifier.
     *
     * @param id the step id
     * @return the step definition, or {@code null} if not found
     */
    public StepDefinition step(StepId id) {
        return steps.get(id);
    }

    /**
     * Builder for {@link FlowDefinition}.
     *
     * @author gokalp
     */
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
package com.flowsentinel.core.definition;

import com.flowsentinel.core.id.FlowId;
import com.flowsentinel.core.id.StepId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
     */
    public static final class Builder {
        private FlowId id;
        private StepId initialStep;
        private final Map<StepId, StepDefinition> steps = new LinkedHashMap<>();

        /**
         * Sets the flow identifier.
         *
         * @param id the flow id
         * @return this builder
         */
        public Builder id(FlowId id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the initial step.
         *
         * @param step the initial step id
         * @return this builder
         */
        public Builder initialStep(StepId step) {
            this.initialStep = step;
            return this;
        }

        /**
         * Adds a step definition to the flow.
         *
         * @param step the step definition
         * @return this builder
         * @throws IllegalArgumentException if a step with the same id is already present
         */
        public Builder putStep(StepDefinition step) {
            Objects.requireNonNull(step, "The step definition cannot be null.");
            if (steps.put(step.id(), step) != null) {
                throw new IllegalArgumentException("The step id '" + step.id() + "' is already defined.");
            }
            return this;
        }

        /**
         * Builds the immutable {@link FlowDefinition}.
         *
         * @return the built flow definition
         */
        public FlowDefinition build() {
            return new FlowDefinition(this);
        }
    }
}
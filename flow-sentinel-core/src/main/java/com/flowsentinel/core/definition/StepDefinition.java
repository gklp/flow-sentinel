package com.flowsentinel.core.definition;

import com.flowsentinel.core.id.StepId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Definition of a single step in a flow.
 * <ul>
 *   <li>{@link NavigationType#SIMPLE}: exactly one transition.</li>
 *   <li>{@link NavigationType#COMPLEX}: one or more transitions evaluated in order.</li>
 * </ul>
 * Instances are immutable; use the builder to construct valid definitions.
 *
 * @author gokalp
 */
public final class StepDefinition {
    private final StepId id;
    private final NavigationType navigationType;
    private final List<Transition> transitions; // ordered

    private StepDefinition(Builder b) {
        this.id = Objects.requireNonNull(b.id, "The id cannot be null.");
        this.navigationType = Objects.requireNonNull(b.navigationType, "The navigationType cannot be null.");
        if (b.transitions.isEmpty()) {
            throw new IllegalArgumentException("A step definition must declare at least one transition (or EOF).");
        }
        if (navigationType == NavigationType.SIMPLE && b.transitions.size() > 1) {
            throw new IllegalArgumentException("A step with SIMPLE navigation can declare only one transition.");
        }
        this.transitions = List.copyOf(b.transitions);
    }

    /**
     * The unique step identifier.
     *S
     * @return the step identifier
     */
    public StepId id() {
        return id;
    }

    /**
     * The navigation type governing how transitions are evaluated.
     *
     * @return the navigation type
     */
    public NavigationType navigationType() {
        return navigationType;
    }

    /**
     * The ordered list of transitions.
     *
     * @return an immutable list of transitions
     */
    public List<Transition> transitions() {
        return transitions;
    }

    /**
     * Builder with defensive validation to keep the type immutable and safe.
     */
    public static final class Builder {
        private StepId id;
        private NavigationType navigationType = NavigationType.SIMPLE;
        private final List<Transition> transitions = new ArrayList<>();

        /**
         * Sets the step identifier.
         *
         * @param id the step id
         * @return this builder
         */
        public Builder id(StepId id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the navigation type.
         *
         * @param type the navigation type
         * @return this builder
         */
        public Builder navigationType(NavigationType type) {
            this.navigationType = type;
            return this;
        }

        /**
         * Adds a transition to this step definition.
         *
         * @param t the transition to add
         * @return this builder
         */
        public Builder addTransition(Transition t) {
            this.transitions.add(Objects.requireNonNull(t, "The transition cannot be null."));
            return this;
        }

        /**
         * Builds an immutable {@link StepDefinition} instance.
         *
         * @return the built step definition
         */
        public StepDefinition build() {
            return new StepDefinition(this);
        }
    }
}
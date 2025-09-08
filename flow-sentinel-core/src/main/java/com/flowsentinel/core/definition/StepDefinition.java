package com.flowsentinel.core.definition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flowsentinel.core.context.StepId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class StepDefinition {
    private final StepId id;
    private final NavigationType navigationType;
    private final List<Transition> transitions; // ordered

    private StepDefinition(Builder b) {
        this.id = Objects.requireNonNull(b.id, "The id cannot be null.");
        this.navigationType = Objects.requireNonNull(b.navigationType, "The navigationType cannot be null.");
        if (b.transitions.isEmpty()) {
            throw new IllegalArgumentException("A stepId definition must declare at least one transition (or EOF).");
        }
        if (navigationType == NavigationType.SIMPLE && b.transitions.size() > 1) {
            throw new IllegalArgumentException("A stepId with SIMPLE navigation can declare only one transition.");
        }
        this.transitions = List.copyOf(b.transitions);
    }

    @JsonCreator
    public StepDefinition(
            @JsonProperty("id") StepId id,
            @JsonProperty("navigationType") NavigationType navigationType,
            @JsonProperty("transitions") List<Transition> transitions) {
        this.id = Objects.requireNonNull(id, "The id cannot be null.");
        this.navigationType = Objects.requireNonNullElse(navigationType, NavigationType.SIMPLE);

        if (transitions == null || transitions.isEmpty()) {
            throw new IllegalArgumentException("A stepId definition must declare at least one transition (or EOF).");
        }
        if (this.navigationType == NavigationType.SIMPLE && transitions.size() > 1) {
            throw new IllegalArgumentException("A stepId with SIMPLE navigation can declare only one transition.");
        }
        this.transitions = List.copyOf(transitions);
    }

    public StepId id() {
        return id;
    }

    public NavigationType navigationType() {
        return navigationType;
    }

    public List<Transition> transitions() {
        return transitions;
    }


    public static final class Builder {
        private StepId id;
        private NavigationType navigationType = NavigationType.SIMPLE;
        private final List<Transition> transitions = new ArrayList<>();

        public Builder id(StepId id) {
            this.id = id;
            return this;
        }

        public Builder navigationType(NavigationType type) {
            this.navigationType = type;
            return this;
        }

        public Builder addTransition(Transition t) {
            this.transitions.add(Objects.requireNonNull(t, "The transition cannot be null."));
            return this;
        }

        public StepDefinition build() {
            return new StepDefinition(this);
        }
    }
}
package com.flowsentinel.core.definition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flowsentinel.core.context.StepId;
import com.flowsentinel.core.runtime.FlowState;

import java.util.function.Predicate;

public final class Transition {
    private final StepId to;
    private final boolean endOfFlow;
    private final Predicate<FlowState> condition;

    private Transition(StepId to, boolean endOfFlow, Predicate<FlowState> condition) {
        if (to == null && !endOfFlow) {
            throw new IllegalArgumentException("A transition must either lead to another stepId or be an end-of-flow transition.");
        }
        if (to != null && endOfFlow) {
            throw new IllegalArgumentException("A transition cannot both lead to another stepId and be an end-of-flow transition.");
        }
        this.to = to;
        this.endOfFlow = endOfFlow;
        this.condition = condition;
    }

    @JsonCreator
    public Transition(@JsonProperty("to") StepId to, @JsonProperty("endOfFlow") Boolean endOfFlow) {
        this(to, (endOfFlow != null && endOfFlow), state -> true);
    }

    public static Transition to(StepId to) {
        return new Transition(to, false, state -> true);
    }

    public static Transition when(StepId to, Predicate<FlowState> condition) {
        return new Transition(to, false, condition);
    }

    public static Transition eof() {
        return new Transition(null, true, state -> true);
    }

    public boolean isSatisfied(FlowState state) {
        return condition.test(state);
    }

    public boolean isEndOfFlow() {
        return endOfFlow;
    }

    public StepId to() {
        return to;
    }
}
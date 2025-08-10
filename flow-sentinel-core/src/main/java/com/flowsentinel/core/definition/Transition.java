package com.flowsentinel.core.definition;

import com.flowsentinel.core.id.StepId;
import com.flowsentinel.core.runtime.FlowState;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Represents a possible transition from one step to another.
 * A transition can be unconditional (always true) or conditional via a predicate.
 *
 * <p>The engine evaluates transitions in the declared order and picks the first
 * one whose condition is satisfied.</p>
 *
 * @author gokalp
 */
public final class Transition {
    private final StepId to;
    private final boolean endOfFlow;
    private final Predicate<FlowState> condition;

    private Transition(StepId to, boolean endOfFlow, Predicate<FlowState> condition) {
        this.to = to;
        this.endOfFlow = endOfFlow;
        this.condition = condition == null ? s -> true : condition;
    }

    /**
     * Creates an unconditional transition to the given step.
     *
     * @param to the target step identifier
     * @return a transition that is always satisfied
     */
    public static Transition to(StepId to) {
        Objects.requireNonNull(to, "The target stepId cannot be null.");
        return new Transition(to, false, s -> true);
    }

    /**
     * Creates a conditional transition.
     *
     * @param to the target step identifier
     * @param condition the predicate evaluated on the flow state
     * @return a conditional transition
     */
    public static Transition when(StepId to, Predicate<FlowState> condition) {
        Objects.requireNonNull(to, "The target stepId cannot be null.");
        Objects.requireNonNull(condition, "The condition predicate cannot be null.");
        return new Transition(to, false, condition);
    }

    /**
     * Creates an end-of-flow (EOF) transition.
     *
     * @return a transition that ends the flow
     */
    public static Transition eof() { return new Transition(null, true, s -> true); }

    /**
     * Returns {@code true} if the transition condition is satisfied for the provided state.
     *
     * @param state the current flow state
     * @return whether the transition is satisfied
     */
    public boolean isSatisfied(FlowState state) { return condition.test(state); }

    /**
     * Indicates that this transition marks the end of the flow.
     *
     * @return {@code true} if this transition ends the flow
     */
    public boolean isEndOfFlow() { return endOfFlow; }

    /**
     * The target step identifier, or {@code null} if this is an EOF transition.
     *
     * @return the target {@code StepId} or {@code null}
     */
    public StepId to() { return to; }
}
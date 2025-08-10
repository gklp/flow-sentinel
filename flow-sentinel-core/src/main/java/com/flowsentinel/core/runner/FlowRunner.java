package com.flowsentinel.core.runner;

import com.flowsentinel.core.engine.FlowEngine;
import com.flowsentinel.core.runtime.FlowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Utility for executing a flow until it completes or until
 * a maximum number of steps is reached.
 *
 * @author gokalp
 */
public final class FlowRunner {

    private static final Logger log = LoggerFactory.getLogger(FlowRunner.class);

    private final FlowEngine engine;

    /**
     * Creates a new runner with the given engine.
     *
     * @param engine engine to use
     */
    public FlowRunner(FlowEngine engine) {
        this.engine = Objects.requireNonNull(engine, "The engine cannot be null.");
    }

    /**
     * Runs the given flow state until completion or until {@code maxSteps} is reached.
     *
     * @param state flow state to run
     * @param maxSteps maximum number of steps to execute before stopping
     * @return execution report
     * @throws IllegalArgumentException if {@code maxSteps} <= 0
     */
    public ExecutionReport runToEnd(FlowState state, int maxSteps) {
        Objects.requireNonNull(state, "The state cannot be null.");
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("The maxSteps must be greater than zero.");
        }
        log.info("Starting flow [{}] execution", state.definition().id());
        int steps = 0;
        while (!state.isCompleted() && steps < maxSteps) {
            engine.next(state);
            steps++;
        }
        if (!state.isCompleted()) {
            log.warn("Flow [{}] stopped after {} steps without completion", state.definition().id(), steps);
        } else {
            log.info("Flow [{}] completed in {} steps", state.definition().id(), steps);
        }
        return new ExecutionReport(state.isCompleted(), steps, state.currentStep());
    }
}

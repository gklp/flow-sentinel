package com.flowsentinel.core.runner;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.engine.FlowEngine;
import com.flowsentinel.core.engine.FlowEngineException;
import com.flowsentinel.core.id.FlowKey;
import com.flowsentinel.core.parser.FlowDefinitionParser;
import com.flowsentinel.core.runtime.FlowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A high-level utility for orchestrating the execution of a flow from start to finish.
 * <p>
 * The {@code FlowRunner} simplifies the process of running a flow by handling
 * the sequence of calls to the {@link FlowEngine}: starting, advancing step-by-step,
 * and handling completion or exceptions. It is particularly useful for scenarios
 * like automated testing, batch processing, or any situation where a flow needs
 * to be executed synchronously.
 * <p>
 * It relies on a configured {@link FlowEngine} for the actual execution logic
 * and a {@link FlowDefinitionParser} to load flow definitions from external sources.
 *
 * @see FlowEngine
 * @see FlowDefinitionParser
 * @see ExecutionReport
 */
public final class FlowRunner {

    private static final Logger log = LoggerFactory.getLogger(FlowRunner.class);

    private final FlowEngine engine;
    private final FlowDefinitionParser parser;

    /**
     * Creates a new runner with the specified engine and parser.
     *
     * @param engine The {@link FlowEngine} to use for flow execution.
     * @param parser The {@link FlowDefinitionParser} to load flow definitions.
     */
    public FlowRunner(FlowEngine engine, FlowDefinitionParser parser) {
        this.engine = Objects.requireNonNull(engine, "The engine cannot be null.");
        this.parser = Objects.requireNonNull(parser, "The parser cannot be null.");
    }

    /**
     * Runs a flow to completion using a definition from an {@link InputStream}.
     * A unique flow ID will be generated.
     *
     * @param definitionStream An {@link InputStream} containing the JSON flow definition.
     * @param initialAttributes A map of initial attributes for the flow.
     * @param maxSteps The maximum number of steps to execute before stopping.
     * @return An {@link ExecutionReport} summarizing the result.
     */
    public ExecutionReport runToEnd(InputStream definitionStream, Map<String, Object> initialAttributes, int maxSteps) {
        FlowDefinition definition = parser.parse(definitionStream);
        String flowId = UUID.randomUUID().toString();
        return runToEnd(flowId, definition, initialAttributes, maxSteps);
    }

    /**
     * Runs a flow to completion using a given {@link FlowDefinition}.
     *
     * @param flowId The unique identifier for this flow instance.
     * @param definition The {@link FlowDefinition} to execute.
     * @param initialAttributes A map of initial attributes for the flow.
     * @param maxSteps The maximum number of steps to execute before stopping.
     * @return An {@link ExecutionReport} summarizing the result.
     * @throws IllegalArgumentException if {@code maxSteps <= 0}.
     * @throws FlowEngineException if an error occurs during execution.
     */
    public ExecutionReport runToEnd(String flowId, FlowDefinition definition, Map<String, Object> initialAttributes, int maxSteps) {
        Objects.requireNonNull(flowId, "The flowId cannot be null.");
        Objects.requireNonNull(definition, "The definition cannot be null.");
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("The maxSteps must be greater than zero.");
        }

        log.info("Starting flow run for ID '{}' with definition '{}'", flowId, definition.id());

        var key = new FlowKey(definition.id().value(), "runner", flowId);

        FlowState state = engine.start(key, definition,
                initialAttributes != null ? initialAttributes : Collections.emptyMap());

        int steps = 0;
        // The loop advances the flow as long as it's not completed and max steps are not reached.
        // We pass an empty map for the payload in `advance`, assuming no new data is added during the automatic run.
        while (!state.isCompleted() && steps < maxSteps) {
            state = engine.advance(key, definition, Collections.emptyMap());
            steps++;
        }

        if (!state.isCompleted()) {
            log.warn("Flow '{}' stopped after {} steps without reaching completion.", flowId, steps);
        } else {
            log.info("Flow '{}' completed in {} steps.", flowId, steps);
        }

        return new ExecutionReport(state.isCompleted(), steps, state.currentStep());
    }
}
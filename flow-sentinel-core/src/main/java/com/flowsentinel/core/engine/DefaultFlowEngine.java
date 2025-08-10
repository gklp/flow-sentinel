package com.flowsentinel.core.engine;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.definition.Transition;
import com.flowsentinel.core.runtime.FlowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Default, deterministic implementation of {@link FlowEngine}. It is stateless
 * and thread-safe.
 *
 * @author gokalp
 */
public final class DefaultFlowEngine implements FlowEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultFlowEngine.class);

    @Override
    public void next(FlowState state) {
        if (state == null) {
            throw new IllegalArgumentException("The state cannot be null.");
        }
        if (state.isCompleted()) {
            log.warn("Attempted to advance an already completed flow [{}]", state.definition().id());
            throw new FlowEngineException("The flow is already completed at step: " + state.currentStep());
        }
        FlowDefinition def = state.definition();
        var step = def.step(state.currentStep());
        if (step == null) {
            // Configuration error: the current step is missing in the definition.
            log.error("No step definition found for [{}] in flow [{}]", state.currentStep(), def.id());
            throw new FlowEngineException("The current step '" + state.currentStep() + "' does not exist in the flow definition.");
        }
        List<Transition> transitions = step.transitions();
        Transition matched = null;
        for (Transition t : transitions) {
            if (t.isSatisfied(state)) {
                matched = t;
                break;
            }
        }
        if (matched == null) {
            log.warn("No transition matched for the step [{}] in flow [{}]", step.id(), def.id());
            throw new FlowEngineException("No transition matched for the step: " + step.id());
        }
        if (matched.isEndOfFlow()) {
            state.markCompleted();
            log.info("Flow [{}] reached the end at step [{}]", def.id(), step.id());
            return;
        }
        log.info("Flow [{}] moving from step [{}] to [{}]", def.id(), step.id(), matched.to());
        state.moveTo(matched.to());
    }
}
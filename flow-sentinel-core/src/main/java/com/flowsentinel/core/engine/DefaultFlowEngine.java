package com.flowsentinel.core.engine;

import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.definition.StepDefinition;
import com.flowsentinel.core.definition.Transition;
import com.flowsentinel.core.id.FlowKey;
import com.flowsentinel.core.runtime.FlowState;
import com.flowsentinel.core.store.FlowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The default, reference implementation of the {@link FlowEngine}.
 * <p>
 * This engine is the central component for managing the lifecycle of a flow. It is responsible for
 * creating the initial state, advancing a flow from one step to the next, and retrieving the
 * current state from a persistence layer. The engine itself is stateless and thread-safe,
 * relying on a {@link FlowStore} to manage all stateful data.
 * <p>
 * Its transition logic is designed to be robust, supporting both explicit step targeting (via a payload key)
 * and conditional, rule-based transitions.
 *
 * @see FlowEngine
 * @see FlowStore
 * @see FlowState
 */
public final class DefaultFlowEngine implements FlowEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultFlowEngine.class);

    /**
     * A special key used in the payload map to explicitly specify the target step of a transition.
     * This allows external controllers, like the {@code FlowGuardAspect}, to direct the flow's path.
     */
    private static final String TARGET_STEP_PAYLOAD_KEY = "__targetStep";

    private final FlowStore flowStore;

    /**
     * Constructs a new {@code DefaultFlowEngine} with the specified storage backend.
     *
     * @param flowStore The {@link FlowStore} implementation to be used for all
     *                  persistence operations. Must not be null.
     */
    public DefaultFlowEngine(FlowStore flowStore) {
        this.flowStore = Objects.requireNonNull(flowStore, "FlowStore cannot be null");
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation creates a new {@link FlowState}, persists it via the
     * configured {@link FlowStore}, and returns the initial state.
     *
     * @throws FlowEngineException  if a flow with the given {@code flowKey} already exists.
     * @throws NullPointerException if {@code flowKey} or {@code definition} is null.
     */
    @Override
    public FlowState start(FlowKey flowKey, FlowDefinition definition, Map<String, Object> initialAttributes) {
        Objects.requireNonNull(flowKey, "Flow key cannot be null.");
        Objects.requireNonNull(definition, "FlowDefinition cannot be null.");

        if (flowStore.exists(flowKey.toStorageKey())) {
            throw new FlowEngineException("A flow with key '" + flowKey + "' already exists.");
        }

        log.debug("Starting new flow '{}' with definition '{}'", flowKey, definition.id());
        FlowState initialState = FlowState.create(definition, initialAttributes);

        flowStore.saveSnapshot(initialState.toSnapshot(flowKey.toStorageKey()));

        log.info("Successfully started and persisted new flow '{}'.", flowKey);
        return initialState;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation retrieves the current state, determines the next logical transition
     * based on the {@code payload} and transition conditions, advances the state to the next step,
     * and persists the new state.
     *
     * @param payload A map of data that can influence the transition. It may contain a
     *                special {@value #TARGET_STEP_PAYLOAD_KEY} key to force a specific next step.
     *                This payload is merged into the flow's attributes. Can be null.
     * @throws FlowEngineException  if the flow is not found, is already completed, or if a valid
     *                              next transition cannot be determined.
     * @throws NullPointerException if {@code flowKey} or {@code definition} is null.
     */
    @Override
    public FlowState advance(FlowKey flowKey, FlowDefinition definition, Map<String, Object> payload) {
        Objects.requireNonNull(flowKey, "Flow key cannot be null.");
        Objects.requireNonNull(definition, "FlowDefinition cannot be null.");

        FlowState currentState = getState(flowKey)
                .orElseThrow(() -> new FlowEngineException("Cannot advance flow: No flow found with key '" + flowKey + "'."));

        if (currentState.isCompleted()) {
            throw new FlowEngineException("The flow with key '" + flowKey + "' is already completed and cannot be advanced.");
        }

        StepDefinition currentStepDef = Optional.ofNullable(definition.step(currentState.currentStep()))
                .orElseThrow(() -> new FlowEngineException(
                        "Configuration error: Step '%s' in flow definition '%s' is not defined."
                                .formatted(currentState.currentStep(), definition.id())
                ));

        Transition matchedTransition = findNextTransition(currentStepDef, payload, currentState);
        FlowState nextState = currentState.advance(matchedTransition, payload);

        flowStore.saveSnapshot(nextState.toSnapshot(flowKey.toStorageKey()));
        log.info("Flow '{}' transitioned from step '{}' to '{}'.", flowKey, currentState.currentStep(), nextState.currentStep());

        return nextState;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation retrieves the persisted state from the
     * {@link FlowStore} using the provided key.
     *
     * @throws NullPointerException if {@code flowKey} is null.
     */
    @Override
    public Optional<FlowState> getState(FlowKey flowKey) {
        Objects.requireNonNull(flowKey, "Flow key cannot be null.");
        log.debug("Attempting to retrieve state for flow key: {}", flowKey);
        return flowStore.find(flowKey.toStorageKey());
    }

    /**
     * Finds the appropriate next transition based on a priority order.
     * <p>
     * The logic is as follows:
     * <ol>
     *   <li><b>Explicit Target:</b> If the payload contains the {@value #TARGET_STEP_PAYLOAD_KEY},
     *       it attempts to find a valid, satisfied transition to that specific step.</li>
     *   <li><b>Unambiguous Path:</b> If no target is specified, it checks for a single, uniquely
     *       satisfied transition based on current state attributes.</li>
     * </ol>
     *
     * @param currentStep The definition of the step from which to transition.
     * @param payload     The runtime data payload that might direct the transition.
     * @param state       The current state of the flow, used to evaluate conditions.
     * @return The single, valid {@link Transition} to take.
     * @throws FlowEngineException if no valid transition can be found, or if multiple
     *                             transitions are satisfied without an explicit target (ambiguity).
     */
    private Transition findNextTransition(StepDefinition currentStep, Map<String, Object> payload, FlowState state) {
        if (payload != null && payload.containsKey(TARGET_STEP_PAYLOAD_KEY)) {
            String targetStepName = (String) payload.get(TARGET_STEP_PAYLOAD_KEY);
            log.debug("Explicit transition requested to step: {}", targetStepName);
            return currentStep.transitions().stream()
                    .filter(t -> t.to().value().equals(targetStepName))
                    .findFirst()
                    .filter(t -> t.isSatisfied(state))
                    .orElseThrow(() -> new FlowEngineException(
                            "Illegal transition: No valid transition found from step '%s' to target step '%s'."
                                    .formatted(currentStep.id().value(), targetStepName)
                    ));
        }

        log.debug("Finding next transition from step '{}' based on satisfied conditions.", currentStep.id());
        List<Transition> satisfiedTransitions = currentStep.transitions().stream()
                .filter(t -> t.isSatisfied(state))
                .toList();

        if (satisfiedTransitions.size() == 1) {
            Transition transition = satisfiedTransitions.get(0);
            log.debug("Found unambiguous transition to step: {}", transition.to());
            return transition;
        }

        if (satisfiedTransitions.isEmpty()) {
            throw new FlowEngineException(
                    "Transition error: No satisfied transition found for step '%s'."
                            .formatted(currentStep.id().value())
            );
        }

        throw new FlowEngineException(
                "Ambiguous transition: Multiple transitions are satisfied from step '%s', but no target step was specified."
                        .formatted(currentStep.id().value())
        );
    }
}
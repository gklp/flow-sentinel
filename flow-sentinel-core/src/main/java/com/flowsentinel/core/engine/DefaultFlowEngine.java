package com.flowsentinel.core.engine;

import com.flowsentinel.core.context.FlowContext;
import com.flowsentinel.core.context.FlowKey;
import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.definition.FlowDefinitionProvider;
import com.flowsentinel.core.definition.StepDefinition;
import com.flowsentinel.core.definition.Transition;
import com.flowsentinel.core.provider.PartitionProvider;
import com.flowsentinel.core.runtime.FlowState;
import com.flowsentinel.core.store.FlowAggregate;
import com.flowsentinel.core.store.FlowMeta;
import com.flowsentinel.core.store.FlowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DefaultFlowEngine implements FlowEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultFlowEngine.class);

    private static final String TARGET_STEP_PAYLOAD_KEY = "__targetStep";

    private final FlowStore flowStore;
    private final FlowDefinitionProvider definitionProvider;
    private final PartitionProvider partitionProvider;

    public DefaultFlowEngine(FlowStore flowStore, FlowDefinitionProvider definitionProvider, PartitionProvider partitionProvider) {
        this.flowStore = Objects.requireNonNull(flowStore, "FlowStore cannot be null");
        this.definitionProvider = Objects.requireNonNull(definitionProvider, "FlowDefinitionProvider cannot be null");
        this.partitionProvider = partitionProvider;
    }

    @Override
    public FlowState start(FlowKey flowKey, FlowDefinition definition, Map<String, Object> initialAttributes) {
        FlowState s = previewStart(flowKey, definition, initialAttributes);
        persist(flowKey, s);
        return s;
    }

    @Override
    public FlowState advance(FlowKey flowKey, FlowDefinition definition, Map<String, Object> payload) {
        FlowState s = previewAdvance(flowKey, definition, payload);
        persist(flowKey, s);
        return s;
    }

    @Override
    public Optional<FlowState> getState(FlowKey flowKey) {
        Objects.requireNonNull(flowKey, "Flow key cannot be null.");

        String storageKey = flowKey.toStorageKey();
        var aggOpt = flowStore.loadAggregate(storageKey);
        if (aggOpt.isEmpty() || aggOpt.get().currentSnapshot() == null) {
            log.debug("No aggregate/currentSnapshot found for storage key: {}", storageKey);
            return Optional.empty();
        }

        String definitionName = flowKey.definitionName();
        var definitionOpt = definitionProvider.getDefinition(definitionName);
        if (definitionOpt.isEmpty()) {
            throw new FlowEngineException("Flow definition not found: " + definitionName);
        }

        FlowState state = FlowState.fromSnapshot(definitionOpt.get(), aggOpt.get().currentSnapshot());
        log.debug("Successfully reconstructed flow state for key: {}", flowKey);
        return Optional.of(state);
    }

    @Override
    public FlowState previewStart(FlowKey flowKey, FlowDefinition definition, Map<String, Object> initialAttributes) {
        Objects.requireNonNull(flowKey, "Flow key cannot be null.");
        Objects.requireNonNull(definition, "FlowDefinition cannot be null.");

        String storageKey = flowKey.toStorageKey();
        if (flowStore.exists(storageKey)) {
            throw new FlowEngineException("A flow with key '" + flowKey + "' already exists.");
        }

        log.debug("Preview start flow '{}' with definition '{}'", flowKey, definition.id());
        return FlowState.create(definition, initialAttributes);
    }

    @Override
    public FlowState previewAdvance(FlowKey flowKey, FlowDefinition definition, Map<String, Object> payload) {
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

        Transition nextTransition = findNextTransition(currentStepDef, payload, currentState);
        return currentState.advance(nextTransition, payload);
    }

    @Override
    public void persist(FlowKey flowKey, FlowState state) {
        Objects.requireNonNull(flowKey, "Flow key cannot be null.");
        Objects.requireNonNull(state, "Flow state cannot be null.");
        String storageKey = flowKey.toStorageKey();

        Optional<String> partitionKey = partitionProvider.provide();
        FlowContext ctx = null;
        ctx = partitionKey.map(key -> FlowContext.withPartition(
                flowKey.flowId(),
                flowKey.ownerId(),
                key
        )).orElseGet(() -> FlowContext.of(flowKey.flowId(), flowKey.ownerId()));

        // Build aggregate with the latest snapshot
        var snapshot = state.toSnapshot(storageKey);
        var aggregate = new FlowAggregate(
                FlowMeta.createNew(ctx),
                snapshot,
                List.of()
        );

        flowStore.saveAggregate(aggregate);
        log.info("Persisted aggregate for flow '{}' at step '{}'", flowKey, state.currentStep());
    }

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
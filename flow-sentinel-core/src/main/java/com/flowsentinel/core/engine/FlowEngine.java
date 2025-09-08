package com.flowsentinel.core.engine;

import com.flowsentinel.core.context.FlowKey;
import com.flowsentinel.core.definition.FlowDefinition;
import com.flowsentinel.core.runtime.FlowState;

import java.util.Map;
import java.util.Optional;

public interface FlowEngine {

    // Legacy convenience (calls preview + persist)
    FlowState start(FlowKey flowKey, FlowDefinition definition, Map<String, Object> initialAttributes);

    FlowState advance(FlowKey flowKey, FlowDefinition definition, Map<String, Object> payload);

    Optional<FlowState> getState(FlowKey flowKey);

    // Two-phase
    FlowState previewStart(FlowKey flowKey, FlowDefinition definition, Map<String, Object> initialAttributes);

    FlowState previewAdvance(FlowKey flowKey, FlowDefinition definition, Map<String, Object> payload);

    void persist(FlowKey flowKey, FlowState state);
}
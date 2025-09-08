package com.flowsentinel.starter.web.provider;

import com.flowsentinel.core.context.FlowContext;
import com.flowsentinel.core.provider.FlowIdProvider;

import java.util.UUID;

public class DefaultFlowIdProvider implements FlowIdProvider {

    @Override
    public FlowContext provide() {
        return FlowContext.anonymous(UUID.randomUUID().toString());
    }

}
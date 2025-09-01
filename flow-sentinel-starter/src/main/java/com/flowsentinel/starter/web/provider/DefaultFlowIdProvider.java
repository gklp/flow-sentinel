package com.flowsentinel.starter.web.provider;

import com.flowsentinel.core.id.OwnerContext;

import java.util.UUID;

/**
 * The default {@link FlowIdProvider} implementation.
 * It generates a random UUID as the flowId and does not provide an ownerId.
 * Suitable for anonymous or single-user flows.
 */
public class DefaultFlowIdProvider implements FlowIdProvider {

    @Override
    public OwnerContext provide() {
        // No owner information, just a unique ID for the flow instance.
        return new OwnerContext(UUID.randomUUID().toString(), null);
    }
}
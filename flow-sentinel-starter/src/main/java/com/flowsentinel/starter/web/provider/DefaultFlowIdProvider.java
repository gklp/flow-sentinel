package com.flowsentinel.starter.web.provider;

import com.flowsentinel.core.id.FlowContext;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * The default {@link FlowIdProvider} implementation.
 * It generates a random UUID as the flowId and does not provide an ownerId or partitionKey.
 * Suitable for anonymous or single-user flows.
 */
public class DefaultFlowIdProvider implements FlowIdProvider {

    @Override
    public FlowContext provide() {
        // Anonymous flow - no owner information, just a unique ID for the flow instance.
        return FlowContext.anonymous(UUID.randomUUID().toString());
    }

    @Override
    public FlowContext provide(HttpServletRequest request) {
        // For backward compatibility, delegate to the no-arg version
        return provide();
    }
}
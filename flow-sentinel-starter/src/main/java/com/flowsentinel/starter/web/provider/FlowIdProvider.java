
package com.flowsentinel.starter.web.provider;

import com.flowsentinel.core.id.FlowContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * A provider responsible for extracting the {@link FlowContext} from a request.
 * The context includes the flow's session identifier (flowId), its owner's identifier (ownerId),
 * and partitioning information (partitionKey).
 */
public interface FlowIdProvider {

    /**
     * Provides the flow context from the incoming request.
     *
     * @return A {@link FlowContext} containing the flow, owner, and partition information.
     */
    com.flowsentinel.core.id.FlowContext provide();

    /**
     * Provides the flow context from the incoming request.
     *
     * @param ignoredRequest The current {@link HttpServletRequest}.
     * @return A {@link FlowContext} containing the flow, owner, and partition information.
     */
    default FlowContext provide(HttpServletRequest ignoredRequest) {
        return provide();
    }
}